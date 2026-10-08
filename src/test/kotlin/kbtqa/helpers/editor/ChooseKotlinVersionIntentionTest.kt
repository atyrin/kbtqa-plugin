package kbtqa.helpers.editor

import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBList
import kbtqa.helpers.editor.ChooseKotlinVersionIntention.VersionItem
import kbtqa.helpers.versions.VersionsService.VersionChannel
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Tests for [ChooseKotlinVersionIntention]: where it is offered, how its popup lists the versions,
 * and what choosing one writes. The versions are made up, so nothing goes to the network.
 */
class ChooseKotlinVersionIntentionTest : MavenPomActionTestCase() {

    private val intention = ChooseKotlinVersionIntention()

    private fun properties(body: String) = "<project>\n    <properties>\n        $body\n    </properties>\n</project>"

    private fun isOffered(text: String, fileName: String = "pom.xml"): Boolean {
        myFixture.configureByText(fileName, text)
        return myFixture.availableIntentions.any { it.text == "Choose Kotlin version" }
    }

    fun testOfferedOnlyOnTheKotlinVersionProperty() {
        assertTrue("in the value", isOffered(properties("<kotlin.version>2.4<caret>.20</kotlin.version>")))
        assertTrue("in an empty value", isOffered(properties("<kotlin.version><caret></kotlin.version>")))
        assertTrue("on the name", isOffered(properties("<kotlin.ver<caret>sion>2.4.20</kotlin.version>")))
        assertFalse("in another property", isOffered(properties("<kotlin.compiler.jvmTarget><caret></kotlin.compiler.jvmTarget>")))
        assertFalse("outside <properties>", isOffered("<project>\n    <kotlin.version><caret></kotlin.version>\n</project>"))
        assertFalse("in another file", isOffered(properties("<kotlin.version><caret></kotlin.version>"), fileName = "other.xml"))
    }

    fun testVersionsKeepTheChannelOrderAndAreListedOnce() {
        val channels = listOf(
            VersionChannel("dev", "", listOf("2.5.0-dev-2", "2.4.21", "2.5.0-dev-1"), repositoryUrl = "https://example.com/dev"),
            VersionChannel("experimental", "", listOf("2.5.0-dev-3", "2.5.0-dev-1"), repositoryUrl = "https://example.com/experimental"),
            VersionChannel("Maven Central", "", listOf("2.4.21", "2.4.20")),
        )

        assertEquals(
            listOf(
                // A version Maven Central has needs no repository, so it is listed there only
                VersionItem("2.5.0-dev-2", "dev"),
                // Both JetBrains repositories have this one; it goes to the first
                VersionItem("2.5.0-dev-1", "dev"),
                VersionItem("2.5.0-dev-3", "experimental"),
                VersionItem("2.4.21", "Maven Central"),
                VersionItem("2.4.20", "Maven Central"),
            ),
            intention.versionItems(channels)
        )
    }

    fun testChosenVersionReplacesTheValue() {
        for (body in listOf("<kotlin.version>2.4.20<caret></kotlin.version>", "<kotlin.version<caret>/>")) {
            configurePom(properties(body))
            val tag = PsiTreeUtil.getParentOfType(pom.findElementAt(myFixture.caretOffset), XmlTag::class.java, false)!!
            intention.setVersion(project, myFixture.editor, SmartPointerManager.createPointer(tag), "2.5.0-dev-10290")

            assertEquals(body, listOf("2.5.0-dev-10290"), values(tag(pomText, "properties"), "kotlin.version"))
            assertEquals("<kotlin.version>2.5.0-dev-10290|</kotlin.version>", caretLine())
        }
    }

    fun testGroupTitlesFollowTheListAsShown() {
        val renderer = GroupedListRenderer<VersionItem>({ it.version }, { it.channel })
        fun render(items: List<VersionItem>): List<String> {
            val list = JBList(items)
            return items.indices.flatMap { i ->
                when (val component = renderer.getListCellRendererComponent(list, items[i], i, false, false)) {
                    is JPanel -> listOf(
                        "[" + component.components.filterIsInstance<TitledSeparator>().single().text + "]",
                        component.components.filterIsInstance<JLabel>().single().text
                    )
                    else -> listOf((component as JLabel).text)
                }
            }
        }
        val items = listOf(
            VersionItem("2.5.0-dev-2", "dev"),
            VersionItem("2.5.0-dev-1", "dev"),
            VersionItem("2.5.0-dev-3", "experimental"),
            VersionItem("2.4.21", "Maven Central"),
        )

        assertEquals(listOf("[dev]", "2.5.0-dev-2", "2.5.0-dev-1", "[experimental]", "2.5.0-dev-3", "[Maven Central]", "2.4.21"), render(items))
        // Filtered down, a group whose first item is gone still gets its title
        assertEquals(listOf("[dev]", "2.5.0-dev-1", "[experimental]", "2.5.0-dev-3"), render(items.filter { "dev-" in it.version && it.version != "2.5.0-dev-2" }))
    }
}
