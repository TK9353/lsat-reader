package com.taehyeon.lsatreader.data

/**
 * 기본 RSS/Atom 목록. 형식: 주제 | 이름 | URL  (설정 화면에서 수정 가능)
 * 유료 구독 기사는 본문 추출이 되지 않으면 피드에서 자동으로 건너뜀.
 */
object DefaultFeeds {
    val TEXT = """
# 주제 | 이름 | URL   (# 으로 시작하는 줄은 무시)
POLITICS | Brookings | https://www.brookings.edu/feed/
POLITICS | CRS Reports (EveryCRSReport) | https://www.everycrsreport.com/rss.xml
POLITICS | The Conversation – Politics | https://theconversation.com/us/politics/articles.atom
POLITICS | NYT Opinion | https://rss.nytimes.com/services/xml/rss/nyt/Opinion.xml
ECONOMICS | Liberty Street Economics (NY Fed) | https://libertystreeteconomics.newyorkfed.org/feed/
ECONOMICS | The Conversation – Economy | https://theconversation.com/us/economy/articles.atom
ECONOMICS | Project Syndicate | https://www.project-syndicate.org/rss
HISTORY | Smithsonian – History | https://www.smithsonianmag.com/rss/history/
HISTORY | History Today | https://www.historytoday.com/feed/rss.xml
HISTORY | JSTOR Daily | https://daily.jstor.org/feed/
TECHNOLOGY | MIT Technology Review | https://www.technologyreview.com/feed/
TECHNOLOGY | Ars Technica | https://feeds.arstechnica.com/arstechnica/index
TECHNOLOGY | The Conversation – Technology | https://theconversation.com/us/technology/articles.atom
SCIENCE | Quanta Magazine | https://www.quantamagazine.org/feed/
SCIENCE | Knowable Magazine | https://knowablemagazine.org/rss
SCIENCE | The Conversation – Science | https://theconversation.com/us/science-technology/articles.atom
LAW | SCOTUSblog | https://www.scotusblog.com/feed/
LAW | Lawfare | https://www.lawfaremedia.org/feeds/articles
LAW | The Conversation – Law | https://theconversation.com/us/topics/law-1123/articles.atom
PHILOSOPHY | Aeon Essays | https://aeon.co/feed.rss
PHILOSOPHY | Psyche | https://psyche.co/feed
PHILOSOPHY | Noema | https://www.noemamag.com/feed/
CULTURE | The Atlantic | https://www.theatlantic.com/feed/all/
CULTURE | The Conversation – Arts & Culture | https://theconversation.com/us/arts/articles.atom
CULTURE | Aeon Essays | https://aeon.co/feed.rss
""".trimIndent()
}
