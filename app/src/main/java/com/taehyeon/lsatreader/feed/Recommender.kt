package com.taehyeon.lsatreader.feed

import com.taehyeon.lsatreader.data.Article
import com.taehyeon.lsatreader.data.Kind
import com.taehyeon.lsatreader.data.Store
import com.taehyeon.lsatreader.data.Topic
import kotlin.math.exp
import kotlin.random.Random

/**
 * 관심사 학습 알고리즘 (릴스식)
 * - 주제별·공급원별 가중치 w (초기 1.0, 범위 0.2~5.0)
 * - 체류시간 비율 r = 실제 체류 / 예상 읽기시간(230 wpm) 로 갱신:
 *     4초 미만 스킵 → w *= e^-0.15,  그 외 → w *= e^{0.6·(min(r,1.2) − 0.25)}
 * - 좋아요 → w *= e^0.4
 * - 선택: 15% 확률로 무작위 탐색(ε-greedy), 나머지는 w에 비례한 확률로 추첨
 */
class Recommender(private val store: Store) {
    private val weights: MutableMap<String, Double> = store.loadWeights()

    private fun key(t: Topic) = "topic:${t.name}"
    private fun key(k: Kind) = "kind:${k.name}"

    fun weight(t: Topic) = weights[key(t)] ?: 1.0
    fun weight(k: Kind) = weights[key(k)] ?: 1.0

    private fun <T> weightedPick(items: List<T>, w: (T) -> Double): T {
        if (Random.nextDouble() < EPSILON) return items.random()
        val total = items.sumOf { w(it) }
        var x = Random.nextDouble() * total
        for (it in items) {
            x -= w(it)
            if (x <= 0) return it
        }
        return items.last()
    }

    fun pickTopic(enabled: Set<Topic>): Topic = weightedPick(enabled.toList().ifEmpty { Topic.entries }) { weight(it) }

    fun pickKind(available: List<Kind>): Kind = weightedPick(available) { weight(it) }

    private fun bump(k: String, delta: Double) {
        val v = (weights[k] ?: 1.0) * exp(delta)
        weights[k] = v.coerceIn(0.2, 5.0)
    }

    fun onDwell(a: Article, dwellMs: Long) {
        val expected = (a.wordCount / 230.0 * 60_000).coerceAtLeast(20_000.0)
        val delta = if (dwellMs < 4_000) -0.15 else 0.6 * ((dwellMs / expected).coerceAtMost(1.2) - 0.25)
        bump(key(a.topic), delta)
        bump(key(a.kind), delta * 0.5)
        store.saveWeights(weights)
    }

    fun onLike(a: Article, liked: Boolean) {
        val d = if (liked) 0.4 else -0.4
        bump(key(a.topic), d)
        bump(key(a.kind), d * 0.5)
        store.saveWeights(weights)
    }

    fun snapshot(): Map<String, Double> = HashMap(weights)

    fun reset() {
        weights.clear()
        store.saveWeights(weights)
    }

    companion object {
        const val EPSILON = 0.15
    }
}
