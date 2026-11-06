package org.bitwisemadness.abilityhotbar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Menu labels (hotbar#139): the convention translation keys, the prettified
 * fallbacks the issue pins, and the grep gate that keeps raw vocabulary ids
 * out of the screen (ids reach players only through {@link MenuLabels}).
 */
class MenuLabelsTest {

    private static final ResourceLocation CATEGORY =
            ResourceLocation.fromNamespaceAndPath("tensura", "magic");

    @Test
    void conventionKeysStayValidTranslationIds() {
        assertEquals("ability_hotbar.category.tensura.magic", MenuLabels.categoryKey(CATEGORY),
                "namespace and path joined with dots");
        assertEquals("ability_hotbar.category.tensura.skill.mastery",
                MenuLabels.categoryKey(
                        ResourceLocation.fromNamespaceAndPath("tensura", "skill/mastery")),
                "path separators become dots");
        assertEquals("ability_hotbar.tag.type.unique", MenuLabels.tagKey("type:unique"),
                "tag separators become dots");
        assertEquals("ability_hotbar.tag.battlewill_style", MenuLabels.tagKey("battlewill_style"),
                "underscores are legal in translation ids and stay");
        assertEquals("ability_hotbar.provider.tensura", MenuLabels.providerKey("tensura"));
    }

    @Test
    void fallbacksPrettifyInsteadOfShowingRawIds() {
        assertEquals("Magic", MenuLabels.prettify(CATEGORY.getPath()),
                "the category fallback is the prettified path");
        assertEquals("Battlewill style", MenuLabels.prettify("battlewill_style"),
                "the issue's example: separators to spaces, first letter capitalised");
        assertEquals("Type unique", MenuLabels.prettify("type:unique"),
                "a tag's separator never reaches the player as punctuation");
        assertEquals("Common skills", MenuLabels.prettify("common/skills"));
        assertEquals("", MenuLabels.prettify(""));
        assertEquals("", MenuLabels.prettify(null));
    }

    @Test
    void subcategoriesStripTheirTypePrefix() {
        // hotbar#142: the category cycle reads "Skill / Unique"
        assertEquals("Unique", MenuLabels.subcategoryBare("type:unique"),
                "the type: prefix never reaches the player");
        assertEquals("Summoning", MenuLabels.subcategoryBare("type:summoning"));
        assertEquals("Bare", MenuLabels.subcategoryBare("bare"),
                "a tag without the prefix prettifies as-is");
        assertEquals("ability_hotbar.tag.type.unique", MenuLabels.tagKey("type:unique"),
                "one provider translation covers the tag chip and the cycle");
    }

    @Test
    void theScreenNeverRendersRawVocabularyIds() throws Exception {
        // every id reaches the player through MenuLabels (issue #139) - the
        // raw sites the issue lists must not come back. Persisting the raw
        // preference in onClose is correct and stays (the config stores ids).
        Path source = Path.of("src", "main", "java", "org", "bitwisemadness",
                "abilityhotbar", "client", "AbilityMenuScreen.java");
        assertTrue(Files.exists(source), "run from the project root (gradle does)");
        String text = Files.readString(source);
        assertTrue(text.contains("MenuLabels.category(categoryFilter)"),
                "the category chip labels through MenuLabels");
        assertTrue(text.contains("MenuLabels.tag(tagFilter)"),
                "the tag chip labels through MenuLabels");
        assertTrue(text.contains("MenuLabels.category(definition.category())"),
                "the meta line labels the category through MenuLabels");
        assertFalse(text.contains("meta.append(definition.category())"),
                "the meta line never appends the raw id");
        assertFalse(text.contains("String.join(\", \", definition.tags())"),
                "the meta line never joins raw tags");
    }
}
