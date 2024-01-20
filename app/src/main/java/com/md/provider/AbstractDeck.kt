package com.md.provider

open class AbstractDeck {
    @JvmField
	var name: String? = null
    @JvmField
	var id = 0

    companion object {
        const val NAME = "name"
    }
}