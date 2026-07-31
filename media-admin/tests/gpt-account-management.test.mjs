import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const apiSource = await readFile(new URL('../src/api/system.js', import.meta.url), 'utf8')
const viewSource = await readFile(new URL('../src/views/gpt/GptAccountView.vue', import.meta.url), 'utf8')

test('admin exposes secure GPT account management workflow', () => {
  assert.match(apiSource, /gptAccountApi/)
  assert.match(apiSource, /\/api\/gpt\/accounts\/import/)
  assert.match(apiSource, /\/api\/gpt\/accounts\/refresh/)
  assert.match(apiSource, /\/api\/gpt\/accounts\/\$\{id\}\/used/)
  assert.match(apiSource, /removeBatch/)
  assert.match(viewSource, /导入 Access Token/)
  assert.match(viewSource, /免费 Plus 资格/)
  assert.match(viewSource, /使用状态/)
  assert.match(viewSource, /updateUsed/)
  assert.match(viewSource, /删除选中/)
  assert.match(viewSource, /gpt:account:refresh/)
  assert.match(viewSource, /待结算确认/)
  assert.match(viewSource, /tokenFingerprint/)
  assert.doesNotMatch(viewSource, /row\.accessToken/)
})
