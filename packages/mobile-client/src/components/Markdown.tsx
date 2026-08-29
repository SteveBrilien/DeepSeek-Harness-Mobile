import { Fragment, type ReactNode } from 'react'

const inlinePattern = /(`[^`]+`|\*\*[^*]+\*\*|\[[^\]]+\]\(https?:\/\/[^\s)]+\))/g

function Inline({ text }: { text: string }) {
  const nodes: ReactNode[] = []
  let cursor = 0
  for (const match of text.matchAll(inlinePattern)) {
    const index = match.index
    if (index > cursor) nodes.push(text.slice(cursor, index))
    const value = match[0]
    if (value.startsWith('`')) nodes.push(<code key={index}>{value.slice(1, -1)}</code>)
    else if (value.startsWith('**')) nodes.push(<strong key={index}>{value.slice(2, -2)}</strong>)
    else {
      const parts = /^\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)$/.exec(value)
      if (parts) nodes.push(<a key={index} href={parts[2]} target="_blank" rel="noreferrer">{parts[1]}</a>)
    }
    cursor = index + value.length
  }
  if (cursor < text.length) nodes.push(text.slice(cursor))
  return <>{nodes}</>
}

function highlightJavascript(code: string): ReactNode[] {
  const token = /(\/\*[\s\S]*?\*\/|\/\/[^\n]*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`|\b(?:const|let|var|function|return|if|else|for|while|await|async|class|new|import|from|export|throw|try|catch|finally|switch|case|break|true|false|null|undefined)\b|\b\d+(?:\.\d+)?\b)/g
  return code.split(token).filter(Boolean).map((part, index) => {
    let kind = 'plain'
    if (/^(\/\*|\/\/)/.test(part)) kind = 'comment'
    else if (/^["'`]/.test(part)) kind = 'string'
    else if (/^\d/.test(part)) kind = 'number'
    else if (/^[a-z]/.test(part) && part !== 'true' && part !== 'false' && part !== 'null' && part !== 'undefined') kind = 'keyword'
    else if (/^(true|false|null|undefined)$/.test(part)) kind = 'literal'
    return kind === 'plain' ? <Fragment key={index}>{part}</Fragment> : <span key={index} className={`tok-${kind}`}>{part}</span>
  })
}

function CodeBlock({ language, code }: { language: string; code: string }) {
  const js = /^(js|jsx|javascript|ts|tsx|typescript)$/.test(language.toLowerCase())
  return <div className="code-block"><div className="code-header"><span>{language || 'text'}</span><button onClick={() => navigator.clipboard?.writeText(code)}>复制</button></div><pre><code>{js ? highlightJavascript(code) : code}</code></pre></div>
}

export function Markdown({ text }: { text: string }) {
  const rows = text.replace(/\r\n/g, '\n').split('\n')
  const blocks: ReactNode[] = []
  let paragraph: string[] = []
  let list: string[] = []
  let fence: { language: string; lines: string[] } | null = null
  const flushParagraph = () => { if (paragraph.length) { blocks.push(<p key={`p${blocks.length}`}><Inline text={paragraph.join('\n')}/></p>); paragraph = [] } }
  const flushList = () => { if (list.length) { blocks.push(<ul key={`l${blocks.length}`}>{list.map((item, i) => <li key={i}><Inline text={item}/></li>)}</ul>); list = [] } }
  for (const row of rows) {
    const marker = /^```\s*([^\s]*)/.exec(row)
    if (marker) {
      if (fence) { blocks.push(<CodeBlock key={`c${blocks.length}`} language={fence.language} code={fence.lines.join('\n')}/>); fence = null }
      else { flushParagraph(); flushList(); fence = { language: marker[1] || '', lines: [] } }
      continue
    }
    if (fence) { fence.lines.push(row); continue }
    const heading = /^(#{1,3})\s+(.+)$/.exec(row)
    const bullet = /^[-*]\s+(.+)$/.exec(row)
    if (heading) {
      flushParagraph(); flushList()
      const content = <Inline text={heading[2]}/>
      blocks.push(heading[1].length === 1 ? <h1 key={`h${blocks.length}`}>{content}</h1> : heading[1].length === 2 ? <h2 key={`h${blocks.length}`}>{content}</h2> : <h3 key={`h${blocks.length}`}>{content}</h3>)
    } else if (bullet) { flushParagraph(); list.push(bullet[1]) }
    else if (!row.trim()) { flushParagraph(); flushList() }
    else { flushList(); paragraph.push(row) }
  }
  if (fence) blocks.push(<CodeBlock key={`c${blocks.length}`} language={fence.language} code={fence.lines.join('\n')}/>)
  flushParagraph(); flushList()
  return <div className="markdown-body">{blocks}</div>
}
