/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * This file comes from https://github.com/thundernest/k-9/tree/main/app/html-cleaner
 */
package com.infomaniak.html.cleaner

import org.jsoup.nodes.*
import org.jsoup.parser.Tag
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

private val ALLOWED_TAGS = listOf("style", "meta", "base")

internal class HeadCleaner {
    fun clean(dirtyDocument: Document, cleanedDocument: Document) {
        copySafeNodes(dirtyDocument.head(), cleanedDocument.head())
    }

    private fun copySafeNodes(source: Element, destination: Element) {
        val cleaningVisitor = CleaningVisitor(source, destination)
        NodeTraversor.traverse(cleaningVisitor, source)
    }
}

internal class CleaningVisitor(
    private val root: Element,
    private var destination: Element,
) : NodeVisitor {
    private var elementToSkip: Element? = null

    override fun head(node: Node, depth: Int) {
        if (elementToSkip != null) return

        if (node is Element) {
            if (isSafeTag(node)) {
                val sourceTag = node.tagName()
                val destinationAttributes = node.attributes().clone()
                val destinationChild = Element(Tag.valueOf(sourceTag), node.baseUri(), destinationAttributes)
                destination.appendChild(destinationChild)
                destination = destinationChild
            } else if (node !== root) {
                elementToSkip = node
            }
        } else if (node is TextNode) {
            val destinationText = TextNode(node.wholeText)
            destination.appendChild(destinationText)
        } else if (node is DataNode && isSafeTag(node.parent())) {
            val destinationData = DataNode(node.wholeData)
            destination.appendChild(destinationData)
        }
    }

    override fun tail(source: Node, depth: Int) {
        if (source === elementToSkip) {
            elementToSkip = null
        } else if (source is Element && isSafeTag(source)) {
            destination = destination.parent() ?: error("Missing parent")
        }
    }

    private fun isSafeTag(node: Node?): Boolean {
        if (node == null || isMetaRefresh(node)) return false

        val tag = node.nodeName().lowercase()
        return tag in ALLOWED_TAGS
    }

    private fun isMetaRefresh(node: Node): Boolean {
        val tag = node.nodeName().lowercase()
        if (tag != "meta") return false

        val attributeValue = node.attributes().getIgnoreCase("http-equiv").trim().lowercase()
        return attributeValue == "refresh"
    }
}
