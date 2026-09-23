package com.qqbot.keywordbot.bot

import com.qqbot.keywordbot.data.entity.KeywordReply
import com.qqbot.keywordbot.data.entity.MatchType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 关键词匹配器。
 * 支持 exact / contains / regex 三种匹配方式。
 */
@Singleton
class KeywordMatcher @Inject constructor() {

    /**
     * 在规则列表中匹配第一条命中的规则。
     * @return 命中的 KeywordReply，未命中返回 null
     */
    fun match(text: String, rules: List<KeywordReply>): KeywordReply? {
        for (rule in rules) {
            if (!rule.enabled) continue
            if (matches(text, rule)) return rule
        }
        return null
    }

    private fun matches(text: String, rule: KeywordReply): Boolean {
        return when (rule.matchType) {
            MatchType.EXACT -> text == rule.keyword
            MatchType.CONTAINS -> text.contains(rule.keyword, ignoreCase = true)
            MatchType.REGEX -> runCatching {
                Regex(rule.keyword, RegexOption.IGNORE_CASE).containsMatchIn(text)
            }.getOrDefault(false)
        }
    }
}
