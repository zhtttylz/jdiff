package jdiff.doc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Element;

/**
 * Common functionality for {@link Doc} implementations.
 */
abstract class BaseDoc implements Doc {
    private final DocEnvironment environment;
    private final Element element;
    private final String overrideName;

    private Map<String, List<Tag>> tagsByName;
    private List<Tag> allTags;

    BaseDoc(DocEnvironment environment, Element element, String overrideName) {
        this.environment = environment;
        this.element = element;
        this.overrideName = overrideName;
    }

    Element element() {
        return element;
    }

    DocEnvironment environment() {
        return environment;
    }

    @Override
    public String name() {
        if (overrideName != null) {
            return overrideName;
        }
        if (element == null) {
            return "";
        }
        return element.getSimpleName().toString();
    }

    @Override
    public String getRawCommentText() {
        return environment.docComment(element);
    }

    @Override
    public Tag[] tags() {
        ensureTagsParsed();
        if (allTags.isEmpty()) {
            return new Tag[0];
        }
        return allTags.toArray(new Tag[0]);
    }

    @Override
    public Tag[] tags(String name) {
        ensureTagsParsed();
        List<Tag> tags = tagsByName.get(name);
        if (tags == null || tags.isEmpty()) {
            return new Tag[0];
        }
        return tags.toArray(new Tag[0]);
    }

    private void ensureTagsParsed() {
        if (tagsByName != null) {
            return;
        }
        tagsByName = new HashMap<>();
        allTags = new ArrayList<>();
        String raw = getRawCommentText();
        if (raw == null) {
            return;
        }
        String currentName = null;
        StringBuilder currentText = null;
        for (String line : raw.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("@")) {
                if (currentName != null) {
                    addTag(currentName, currentText.toString());
                }
                int space = trimmed.indexOf(' ');
                if (space == -1) {
                    currentName = trimmed.substring(1);
                    currentText = new StringBuilder();
                } else {
                    currentName = trimmed.substring(1, space);
                    currentText = new StringBuilder(trimmed.substring(space + 1).trim());
                }
            } else if (currentName != null) {
                if (currentText.length() != 0) {
                    currentText.append('\n');
                }
                currentText.append(trimmed);
            }
        }
        if (currentName != null) {
            addTag(currentName, currentText.toString());
        }
    }

    private void addTag(String name, String text) {
        Tag tag = new TagImpl(name, text);
        allTags.add(tag);
        tagsByName.computeIfAbsent(name, key -> new ArrayList<>()).add(tag);
    }
}
