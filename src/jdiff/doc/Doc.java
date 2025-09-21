package jdiff.doc;

/**
 * Minimal view of a documented element.
 */
public interface Doc {
    /**
     * Returns the name of the documented element.
     */
    String name();

    /**
     * Returns the raw documentation comment text, or {@code null} if none.
     */
    String getRawCommentText();

    /**
     * Returns all block tags on this element.
     */
    Tag[] tags();

    /**
     * Returns the block tags matching the provided name (without the leading '@').
     */
    Tag[] tags(String name);
}
