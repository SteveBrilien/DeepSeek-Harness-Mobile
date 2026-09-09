import { readFileSync } from "node:fs";
import { boundContextSummary, createUserMessage } from "@deepseek-ai/dsh-llm";

const name = "dsh-mobile-context";
const DEFAULT_CONTEXT_FILE = "/dsh-home/mobile/context.json";

function safeText(value) {
  return typeof value === "string" ? value.trim() : "";
}

function readBootSnapshot() {
  const path = process.env.DSH_MOBILE_CONTEXT_FILE || DEFAULT_CONTEXT_FILE;
  try {
    const raw = JSON.parse(readFileSync(path, "utf8"));
    return {
      schemaVersion: Number(raw.schemaVersion) || 1,
      androidVersion: safeText(raw.androidVersion),
      deviceModel: safeText(raw.deviceModel),
      manufacturer: safeText(raw.manufacturer),
      dshVersion: safeText(raw.dshVersion),
      contextVersion: safeText(raw.contextVersion) || "mobile-context-v2",
    };
  } catch {
    return {
      schemaVersion: 1,
      androidVersion: "",
      deviceModel: "",
      manufacturer: "",
      dshVersion: "",
      contextVersion: "mobile-context-v2",
    };
  }
}

function renderStaticContext(snapshot) {
  const facts = [];
  if (snapshot.androidVersion) facts.push(`Android ${snapshot.androidVersion}`);
  const device = [snapshot.manufacturer, snapshot.deviceModel].filter(Boolean).join(" ");
  if (device) facts.push(device);
  if (snapshot.dshVersion) facts.push(`DSH ${snapshot.dshVersion}`);

  const bootFacts = facts.length === 0 ? "" : ` Boot facts: ${facts.join("; ")}.`;
  return [
    "You are running inside DeepSeek Harness Mobile, an Android host for the official DeepSeek Harness runtime.",
    "The app-managed DSH Web surface already exists; do not start a replacement DSH Web server unless the user explicitly asks for a separate server.",
    "The primary development shell is a PRoot Linux userspace. A `root` identity inside that userspace is not Android UID 0 and does not bypass Android sandboxing or SELinux.",
    "DSH Mobile exposes one terminal surface backed by distinct execution domains: Linux Runtime for normal development, Android Local for app-UID recovery operations, and ADB Shell for Android shell-UID operations when wireless/remote ADB is actually paired and connected.",
    "Prefer the least-privileged execution domain that can complete the task. When automatic routing is available, use it unless the task requires an explicit domain. Never assume ADB, Shizuku, or Android root is available; query the current mobile capability state before privileged Android operations.",
    "DSH_HOME is persistent outside the A/B runtime slots, so DSH settings, credentials, and session storage are not part of the disposable Linux runtime slot.",
    "A Mobile Project is metadata above native DSH workspace/session semantics: the Primary Project maps to the native session cwd; Attached Projects are accessed on demand. Do not permanently enumerate every known project in the prompt.",
    "Runtime capability changes are delivered separately as explicit environment updates or queried through tools; do not infer current dynamic capability state from this boot snapshot.",
  ].join(" ") + bootFacts;
}

function isFreshOrdinarySession(agent, source) {
  const session = agent?.session;
  const header = session?.header;
  if (!session || !header) return false;
  return source === "startup"
    && session.firstLiveSeq === 0
    && header.isSeeded !== true
    && header.origin !== "subagent"
    && !header.parentSession;
}

function buildSessionBootstrap(agent) {
  const cwd = safeText(agent?.session?.header?.cwd) || "(not set)";
  const sections = [
    {
      name: "mobile-project-context",
      text: `Primary working directory for this session: ${cwd}. Treat this as the native DSH session cwd and the default Mobile Project working directory unless the user explicitly attaches or switches projects.`,
    },
    {
      name: "mobile-execution-domains",
      text: "Initial execution-domain contract: Linux Runtime is the default development domain; Android Local is the app-UID recovery domain; ADB Shell is an optional shell-UID domain whose current connection state must be queried before use. Prefer the least-privileged domain that can complete the task.",
    },
    {
      name: "mobile-persistence",
      text: "DSH settings, sessions, and credentials live in persistent DSH_HOME outside disposable A/B runtime slots. Recovery and secret backup mechanisms are separate from the runtime slot and should be used before destructive repair or rebuild operations.",
    },
  ];
  return {
    sections,
    text: sections.map((section) => `[${section.name}]\n${section.text}`).join("\n\n"),
  };
}

function createSessionBootstrapMessage(agent) {
  const bootstrap = buildSessionBootstrap(agent);
  return createUserMessage({
    content: [{ type: "text", text: bootstrap.text }],
    source: {
      kind: "plugin",
      plugin: "@dsh-mobile/dsh-mobile-context",
      form: "snapshot",
      sections: bootstrap.sections,
    },
  });
}

function createEnvironmentNotice(summary, text = summary) {
  const normalizedSummary = boundContextSummary(safeText(summary) || "DSH Mobile environment changed");
  const normalizedText = safeText(text) || normalizedSummary;
  return createUserMessage({
    content: [{ type: "text", text: normalizedText }],
    source: {
      kind: "plugin",
      plugin: "@dsh-mobile/dsh-mobile-context",
      form: "notice",
      summary: normalizedSummary,
    },
  });
}

function injectEnvironmentNotice(agent, summary, text = summary) {
  const message = createEnvironmentNotice(summary, text);
  agent.inject(message);
  return message;
}

function apply(ctx) {
  const snapshot = readBootSnapshot();
  const stableText = renderStaticContext(snapshot);

  ctx.inject(["systemPrompt"], (promptCtx) => {
    promptCtx.systemPrompt.section({
      name: "app:dsh-mobile-context",
      order: promptCtx.systemPrompt.getSectionOrder("WEB_SURFACE") - 10,
      text: () => stableText,
    });
  });

  ctx.on("agent/session-start", ({ agent, source }) => {
    if (!isFreshOrdinarySession(agent, source)) return;
    agent.inject(createSessionBootstrapMessage(agent));
  });
}

export {
  apply,
  buildSessionBootstrap,
  createEnvironmentNotice,
  createSessionBootstrapMessage,
  injectEnvironmentNotice,
  isFreshOrdinarySession,
  name,
  readBootSnapshot,
  renderStaticContext,
};
