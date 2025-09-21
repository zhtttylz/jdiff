package jdiff.doc;

final class TagImpl implements Tag {
    private final String name;
    private final String text;

    TagImpl(String name, String text) {
        this.name = name;
        this.text = text == null ? "" : text.trim();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String text() {
        return text;
    }
}
