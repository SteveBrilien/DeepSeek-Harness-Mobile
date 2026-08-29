import { cp, mkdir, rm, stat } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const source = path.join(root, 'packages', 'mobile-client', 'dist')
const targets = [
  path.join(root, 'packages', 'mobile-gateway', 'public'),
  path.join(root, 'android', 'app', 'src', 'main', 'assets', 'www'),
]

await stat(path.join(source, 'index.html'))
for (const target of targets) {
  const relative = path.relative(root, target)
  if (relative.startsWith('..') || path.isAbsolute(relative)) throw new Error(`refusing target outside v1 root: ${target}`)
  await rm(target, { recursive: true, force: true })
  await mkdir(target, { recursive: true })
  await cp(source, target, { recursive: true })
  console.log(`synced web client -> ${relative}`)
}
