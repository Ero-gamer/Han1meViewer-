package com.yenaly.han1meviewer.OnAir.common

object OnAirConstants {

    val ONAIR_HOSTNAME = arrayOf(
        "rzvg3jy7zxdeudsyingur7kcxbyw3kdirls3wydhidqxjienzh7obmad.onion",
        "onairbpkr2pyofw3pz4or4rcxiwcqiurudmy6jyy3hfkcprke4pjknad.onion",
        "onair5rcsrchh6z2fg24oapp5xtg4igwotvojmr4g4cvptxlbwjhmbqd.onion",
    )

    val ONAIR_URL = arrayOf(
        "http://rzvg3jy7zxdeudsyingur7kcxbyw3kdirls3wydhidqxjienzh7obmad.onion/",
        "http://onairbpkr2pyofw3pz4or4rcxiwcqiurudmy6jyy3hfkcprke4pjknad.onion/",
        "http://onair5rcsrchh6z2fg24oapp5xtg4igwotvojmr4g4cvptxlbwjhmbqd.onion/",
    )

    const val DEFAULT_PAGE_SIZE = 20

    const val SEARCH_FIELD_TITLE = "title"
    const val SEARCH_FIELD_UPLOADER = "uploader"
    const val SEARCH_FIELD_TAG = "tag"

    const val LENGTH_ANY = "any"
    const val LENGTH_XSHORT = "xshort"
    const val LENGTH_SHORT = "short"
    const val LENGTH_MEDIUM = "medium"
    const val LENGTH_LONG = "long"
    const val LENGTH_XLONG = "xlong"

    const val DATE_ANY = "any"
    const val DATE_DAY = "day"
    const val DATE_WEEK = "week"
    const val DATE_MONTH = "month"
    const val DATE_YEAR = "year"

    const val SORT_NEWEST = "newest"
    const val SORT_OLDEST = "oldest"
    const val SORT_MOST_VIEWED = "most_viewed"
    const val SORT_LEAST_VIEWED = "least_viewed"
    const val SORT_LONGEST = "longest"
    const val SORT_SHORTEST = "shortest"

    fun getPrimaryUrl(): String = OnAirSitePreferences.selectedBaseUrl

    fun getPrimaryHost(): String = OnAirSitePreferences.currentHostname()
}
