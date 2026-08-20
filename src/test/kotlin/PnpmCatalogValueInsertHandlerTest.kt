package dev.wanjas

import junit.framework.TestCase

/** Quoting rules for values that would not survive as a plain YAML scalar. */
class PnpmCatalogValueInsertHandlerTest : TestCase() {

    fun testOrdinaryRangesStayUnquoted() {
        assertEquals("^18.3.1", PnpmCatalogValueInsertHandler.toYamlLiteral("^18.3.1"))
        assertEquals("~18.3.1", PnpmCatalogValueInsertHandler.toYamlLiteral("~18.3.1"))
        assertEquals("18.3.1", PnpmCatalogValueInsertHandler.toYamlLiteral("18.3.1"))
        assertEquals("latest", PnpmCatalogValueInsertHandler.toYamlLiteral("latest"))
        assertEquals("<2.0.0", PnpmCatalogValueInsertHandler.toYamlLiteral("<2.0.0"))
    }

    fun testYamlIndicatorsAreQuoted() {
        // `*` is an alias indicator and `>` a folded-block indicator: unquoted, both break the file.
        assertEquals("'*'", PnpmCatalogValueInsertHandler.toYamlLiteral("*"))
        assertEquals("'>=1.0.0'", PnpmCatalogValueInsertHandler.toYamlLiteral(">=1.0.0"))
    }

    fun testEmbeddedQuotesAreEscaped() {
        assertEquals("'*it''s'", PnpmCatalogValueInsertHandler.toYamlLiteral("*it's"))
    }
}
