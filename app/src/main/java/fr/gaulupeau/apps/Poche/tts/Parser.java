package fr.gaulupeau.apps.Poche.tts;

import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Parser {

    private static final String TAG = Parser.class.getSimpleName();

    private static final Pattern SENTENCE_END_PATTERN = Pattern.compile("[.?!\u2026]+\\s");

    private static final Set<String> EMPHASIS_TAGS = new HashSet<>(Arrays.asList(
            "b", "i", "strong", "em"
    ));

    private static abstract class VisitorAdapter implements NodeVisitor {

        private Node currentlySkipping;

        @Override
        public void head(Node node, int depth) {
            if (currentlySkipping != null) return;

            if (shouldSkip(node)) {
                currentlySkipping = node;
                return;
            }

            if (node.childNodeSize() > 0) {
                enterNode(node);
            } else {
                processLeafNode(node);
            }
        }

        @Override
        public void tail(Node node, int depth) {
            if (node == currentlySkipping) {
                currentlySkipping = null;
                return;
            }

            if (node.childNodeSize() > 0) {
                leaveNode(node);
            }
        }

        boolean shouldSkip(Node node) {
            return false;
        }

        abstract void enterNode(Node node);

        abstract void leaveNode(Node node);

        abstract void processLeafNode(Node node);

    }

    static class Range {
        Node start;
        int startOffset;
        Node end;
        int endOffset;

        Range copy() {
            Range range = new Range();
            range.start = start;
            range.startOffset = startOffset;
            range.end = end;
            range.endOffset = endOffset;
            return range;
        }

        @Override
        public String toString() {
            return "Range{" +
                    "start=" + start +
                    ", startOffset=" + startOffset +
                    ", end=" + end +
                    ", endOffset=" + endOffset +
                    '}';
        }
    }

    static class EmphasisExtra {
        int start, end;

        EmphasisExtra(int start, int end) {
            this.start = start;
            this.end = end;
        }

        EmphasisExtra copy() {
            return new EmphasisExtra(start, end);
        }

        @Override
        public String toString() {
            return "EmphasisExtra{" +
                    "start=" + start +
                    ", end=" + end +
                    '}';
        }
    }

    private Node rootNode;

    private StringBuilder accumulatedText = new StringBuilder();
    private Range currentRange = new Range();
    private List<EmphasisExtra> extras = new LinkedList<>();
    private LinkedList<Integer> emphasisStarts = new LinkedList<>();

    private static void replaceNewLinesWithSpaces(StringBuilder sb, int startOffset) {
        for (int i = startOffset; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c == '\n' || c == '\r') {
                sb.setCharAt(i, ' ');
            }
        }
    }

    private static boolean isBlank(CharSequence s) {
        if (s.length() == 0) return true;

        return countWhitespacesFromStart(s) == s.length();
    }

    private static int countWhitespacesFromStart(CharSequence s) {
        int i;
        for (i = 0; i < s.length(); i++) {
            int c = s.charAt(i);
            if (!StringUtil.isActuallyWhitespace(c) && !StringUtil.isInvisibleChar(c)) break;
        }
        return i;
    }

    private static int countWhitespacesFromEnd(CharSequence s, int start, int end) {
        int i;
        for (i = end - 1; i >= start; i--) {
            int c = s.charAt(i);
            if (!StringUtil.isActuallyWhitespace(c) && !StringUtil.isInvisibleChar(c)) break;
        }
        return end - i - 1;
    }

    public void parse(String html) {
        Document document = Jsoup.parseBodyFragment(html); // TODO: check

        document.traverse(new VisitorAdapter() {
            @Override
            boolean shouldSkip(Node node) {
                return node instanceof DataNode || node instanceof Comment; // TODO: check
            }

            @Override
            void enterNode(Node node) {
                if (rootNode == null && isRoot(node)) {
                    rootNode = node;
                }
                processBoundary(node, true);
            }

            @Override
            void leaveNode(Node node) {
                processBoundary(node, false);
            }

            @Override
            void processLeafNode(Node node) {
                Parser.this.processLeafNode(node);
            }

            boolean isRoot(Node node) { // TODO: fix hardcode
                return node instanceof Element && "article".equals(((Element) node).normalName());
            }
        });
    }

    private void addText(String text, List<EmphasisExtra> extras, Range range) {
//        Log.i(TAG, String.format("addText(%s, %s, %s)", text, extras, range));
        Log.i(TAG, String.format("addText(%s, %s)", text, extras));
        GenericItem.Range xPathRange = toXPathRange(range);
        Log.d(TAG, "addText() XPath range: " + xPathRange);
        // TODO
    }

    private void addImage(String altText, String title, String src, Range range) {
//        Log.i(TAG, String.format("addImage(%s, %s, %s, %s)", altText, title, src, range));
        Log.i(TAG, String.format("addImage(%s, %s, %s)", altText, title, src));
        GenericItem.Range xPathRange = toXPathRange(range);
        Log.d(TAG, "addImage() XPath range: " + xPathRange);
        // TODO
    }

    private GenericItem.Range toXPathRange(Range range) {
        return new GenericItem.Range(
                getXPathString(range.start, rootNode), range.startOffset,
                getXPathString(range.end, rootNode), range.endOffset
        );
    }

    private void processBoundary(Node node, boolean enter) {
        if (shouldBreak(node)) {
            flushCurrentText();
        } else {
            handleFormatting(node, enter);
        }
    }

    private void processLeafNode(Node node) {
        if (node instanceof TextNode) {
            TextNode textNode = (TextNode) node;
            if (!textNode.isBlank()) {
                if (accumulatedText.length() == 0) {
                    currentRange.start = node;
                    currentRange.startOffset = 0;
                }

                int oldLength = accumulatedText.length();
                accumulatedText.append(textNode.getWholeText());
                replaceNewLinesWithSpaces(accumulatedText, oldLength);

                currentRange.end = node;
                currentRange.endOffset = textNode.getWholeText().length();

                checkForSentenceEnd(textNode);
            }
        } else if (node instanceof Element && "img".equals(((Element) node).normalName())) {
            flushCurrentText();

            Node parent = node.parent();

            Range range = new Range();
            range.start = parent;
            range.end = parent;
            range.startOffset = indexOf(node);
            range.endOffset = range.startOffset + 1;

            Element img = ((Element) node);

            addImage(img.attr("alt"), img.attr("title"), // TODO: check title
                    img.attr("src"), range);
        } else if (shouldBreak(node)) {
            flushCurrentText();
        }
    }

    private boolean shouldBreak(Node node) { // TODO: note: downgrade - no computed style
        if (node instanceof Element) {
            Element element = (Element) node;

            return element.isBlock() || "br".equals(element.normalName());
        }

        return false;
    }

    private void checkForSentenceEnd(TextNode currentNode) {
        if (accumulatedText.length() == 0) return;

        String currentNodeText = currentNode.getWholeText();
        int currentNodeTextLength = currentNodeText.length();

        boolean found = false;

        Matcher matcher = SENTENCE_END_PATTERN.matcher(currentNodeText);
        while (matcher.find()) {
            int index = matcher.end();

            int end = accumulatedText.length() - (currentNodeTextLength - index);

            Range range = currentRange.copy();
            range.endOffset = index;

            processText(range, end);

            accumulatedText.delete(0, end);
            shiftExtras(end);

            currentRange.start = currentNode;
            currentRange.startOffset = index;

            found = true;
        }

        if (found && isBlank(accumulatedText)) {
            accumulatedText.delete(0, accumulatedText.length());
        }
    }

    private void flushCurrentText() {
        int length = accumulatedText.length();
        if (length > 0) {
            processText(currentRange.copy(), length);
        }

        accumulatedText.delete(0, accumulatedText.length());
        extras.clear();
        emphasisStarts.clear();
    }

    private void processText(Range range, int length) {
        int trimFromStart = countWhitespacesFromStart(accumulatedText);
        int trimFromEnd = countWhitespacesFromEnd(accumulatedText, trimFromStart, length);

        if (length - trimFromStart - trimFromEnd > 0) {
            range.startOffset += trimFromStart;
            range.endOffset -= trimFromEnd;

            List<EmphasisExtra> relevantExtras = getRelevantExtras(trimFromStart, length);

            String s = accumulatedText.substring(trimFromStart, length - trimFromEnd);

            addText(s, relevantExtras, range);
        }
    }

    private void handleFormatting(Node node, boolean start) {
        if (node instanceof Element && EMPHASIS_TAGS.contains(((Element) node).normalName())) {
            if (start) {
                emphasisStarts.push(accumulatedText.length());
            } else {
                Integer lastStart = emphasisStarts.poll();
                if (lastStart != null && accumulatedText.length() > 0) {
                    extras.add(new EmphasisExtra(lastStart, accumulatedText.length()));
                }
            }
        }
    }

    private List<EmphasisExtra> getRelevantExtras(int startOffset, int end) {
        List<EmphasisExtra> result = null;

        for (EmphasisExtra extra : extras) {
            if (extra.start < end) {
                EmphasisExtra copy = extra.copy();
                copy.start -= startOffset;
                copy.end -= startOffset;

                if (result == null) result = new ArrayList<>();
                result.add(copy);
            }
        }

        if (emphasisStarts.size() > 0) {
            if (result == null) result = new ArrayList<>(1);
            result.add(new EmphasisExtra(
                    emphasisStarts.getLast() - startOffset,
                    end - startOffset));
        }

        return result;
    }

    private void shiftExtras(int amount) {
        if (amount == 0) return;

        for (int i = 0; i < emphasisStarts.size(); i++) {
            emphasisStarts.set(i, Math.max(emphasisStarts.get(i) - amount, 0));
        }

        for (ListIterator<EmphasisExtra> it = extras.listIterator(); it.hasNext(); ) {
            EmphasisExtra extra = it.next();

            extra.start = Math.max(extra.start - amount, 0);

            extra.end -= amount;
            if (extra.end <= 0) it.remove();
        }
    }

    private String getXPathString(Node node, Node rootNode) {
        StringBuilder sb = new StringBuilder(); // TODO: optimize?

        do {
            int index = indexOf(node);
            sb.insert(0, getXPathPart(node, index)); // TODO: optimize?
        } while (node != rootNode && (node = node.parent()) != null);

        return sb.toString();
    }

    private String getXPathPart(Node node, int index) {
        index++; // 1-based indexing

        if (node instanceof TextNode) {
            return "/text()[" + index + "]";
        } else if (node instanceof Element) {
            String name = ((Element) node).normalName();
            return "/" + name + "[" + index + "]";
        } else {
            Log.e(TAG, "getXPath() don't know how to deal with " + node);
            return null;
        }
    }

    private int indexOf(Node node) {
        Node parent = node.parent();

        if (parent == null) return 0;

        boolean textNode = node instanceof TextNode;
        String name = node instanceof Element ? ((Element) node).normalName() : null;

        if (!textNode && name == null) {
            Log.e(TAG, "indexOf() node type is not supported: " + node);
            return 0;
        }

        boolean found = false;
        int sameTypeSiblings = 0;

        for (Node n : parent.childNodes()) {
            if (n == node) {
                found = true;
                break;
            }

            if (textNode) {
                if (n instanceof TextNode) {
                    sameTypeSiblings++;
                }
            } else if (n instanceof Element && name.equals(((Element) n).normalName())) {
                sameTypeSiblings++;
            }
        }

        if (!found) {
            Log.w(TAG, "getXPath() node index wasn't found");
        }

        return sameTypeSiblings;
    }

}
