# Third-Party Notices

## dsh-client-ui-mobile 0.1.9

- Project: `dsh-client-ui-mobile`
- Upstream: `https://github.com/GithungDang/dsh-client-ui-mobile`
- Package version: `0.1.9`
- License: MIT
- Copyright notice: `Copyright (c) 2026 gihungdang`

DeepSeek Harness Mobile vendors the published 0.1.9 runtime package files (`package.json`, `cordis.patch.yml`, `lib/index.js`, `lib/client.js`) together with the upstream MIT license. The files are copied verbatim from the published package and are not locally rebuilt.

The package is used as an additive Cordis client-layout layer for the official `dsh web` profile on narrow screens. It reuses DSH's built-in layout service and slots rather than replacing the upstream DSH Web Client.

Vendored-file SHA-256 values:

```text
bc5f87f96b31361770ef7cd56c43db0ead78fe1a3e93309f09a3f86091553a4f  package.json
d36f228b8d4ae842fafc22a9e18a1e43bf3339afd3b5fc8d42f9d455e45852f0  lib/client.js
ae55135f8ac8520600d83b95c2ac62772b29adfd8e861e0b24934830d313d1d3  lib/index.js
26780ddc5c14a480645341bc0d606809d9cf0599a4080da8e44aec2dd5f371f1  cordis.patch.yml
66ef15f1d96a34f0b8d788a5716493513ebc88477523299997eaf8e75ce74c3c  LICENSE
```

Compatibility note: the plugin targets the Harness 0.1 series and depends on DSH client layout conventions. Runtime/DSH upgrades must verify the mobile profile before an A/B slot is promoted.
