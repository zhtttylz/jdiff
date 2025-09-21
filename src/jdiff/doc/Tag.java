package jdiff.doc;

/**
 * Minimal representation of a Javadoc tag used by the JDiff doclet.
 */
public interface Tag {
    /**
     * Returns the tag name (without the leading '@').
     */
    String name();

    /**
     * Returns the tag body text.
     */
    String text();
}
