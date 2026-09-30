#!/usr/bin/env bash
# 기본 RSS 목록과 API 엔드포인트가 응답하는지 점검 (CI에서 실행)
UA="Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36"
check() {
  local url="$1"
  local code
  code=$(curl -sL -m 20 -A "$UA" -o /tmp/f.bin -w "%{http_code}" "$url")
  local items full
  items=$(grep -ciE "<item[ >]|<entry[ >]" /tmp/f.bin 2>/dev/null)
  full=$(grep -ci "content:encoded\|<content" /tmp/f.bin 2>/dev/null)
  echo "$code items=$items fulltext_tags=$full | $url"
}
echo "== RSS =="
grep -E '^[A-Z]+ \| .* \| https?://' app/src/main/java/com/taehyeon/lsatreader/data/DefaultFeeds.kt | awk -F'|' '{gsub(/ /,"",$3); print $3}' | while read -r u; do check "$u"; done
echo "== APIs =="
check "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=federalism&format=json"
check "https://content.guardianapis.com/search?api-key=test&page-size=1&show-fields=body"
check "https://api.dictionaryapi.dev/api/v2/entries/en/tenuous"
echo "== Article extraction sample (first link of each feed: word count of <p> text) =="
grep -E '^[A-Z]+ \| .* \| https?://' app/src/main/java/com/taehyeon/lsatreader/data/DefaultFeeds.kt | awk -F'|' '{gsub(/ /,"",$3); print $3}' | while read -r u; do
  curl -sL -m 20 -A "$UA" "$u" -o /tmp/feed.xml
  link=$(python3 - <<'PY'
import re
s=open('/tmp/feed.xml',encoding='utf-8',errors='ignore').read()
m=re.search(r'<item[\s>].*?<link>\s*(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?\s*</link>',s,re.S) or re.search(r'<entry[\s>].*?<link[^>]*href="([^"]+)"',s,re.S)
print(m.group(1).strip() if m else '')
PY
)
  if [ -n "$link" ]; then
    words=$(curl -sL -m 20 -A "$UA" "$link" | python3 -c "
import sys,re,html
s=sys.stdin.read()
ps=re.findall(r'<p[^>]*>(.*?)</p>',s,re.S)
t=' '.join(re.sub(r'<[^>]+>',' ',p) for p in ps)
print(len(re.findall(r'[A-Za-z]+',html.unescape(t))))")
    echo "words=$words | $link"
  else
    echo "no-link | $u"
  fi
done
