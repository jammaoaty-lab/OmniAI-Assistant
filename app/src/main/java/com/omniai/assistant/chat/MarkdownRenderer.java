package com.omniai.assistant.chat;

import android.graphics.Color;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import io.noties.markwon.Markwon;
import io.noties.markwon.core.CorePlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.syntax.Prism4jTheme;
import io.noties.markwon.syntax.Prism4jThemeDefault;
import io.noties.markwon.syntax.SyntaxHighlightPlugin;
import io.noties.prism4j.Prism4j;
import io.noties.prism4j.languages.Prism4jGrammarLocator;

public class MarkdownRenderer {

    private static volatile MarkdownRenderer instance;
    private Markwon markwon;
    private Prism4jTheme codeTheme;

    private MarkdownRenderer() {
        this.codeTheme = Prism4jThemeDefault.create();
    }

    public static MarkdownRenderer getInstance() {
        if (instance == null) {
            synchronized (MarkdownRenderer.class) {
                if (instance == null) {
                    instance = new MarkdownRenderer();
                }
            }
        }
        return instance;
    }

    public void renderMarkdown(TextView textView, String markdown) {
        if (markwon == null) {
            markwon = configureMarkwon(textView);
        }
        markwon.setMarkdown(textView, markdown != null ? markdown : "");
    }

    public Markwon configureMarkwon(TextView textView) {
        if (markwon != null) {
            return markwon;
        }

        Prism4j prism4j = new Prism4j(new Prism4jGrammarLocator() {
            @Override
            public java.util.Set<String> languages() {
                java.util.Set<String> set = new java.util.HashSet<>();
                set.add("java");
                set.add("python");
                set.add("javascript");
                set.add("kotlin");
                set.add("c");
                set.add("cpp");
                set.add("bash");
                set.add("json");
                set.add("xml");
                set.add("sql");
                set.add("go");
                set.add("rust");
                set.add("swift");
                return set;
            }

            @Override
            public io.noties.prism4j.Grammar grammar(String language) {
                return null;
            }
        });

        markwon = Markwon.builder(textView.getContext())
                .usePlugin(CorePlugin.create())
                .usePlugin(HtmlPlugin.create())
                .usePlugin(ImagesPlugin.create())
                .usePlugin(TablePlugin.create(textView.getContext()))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TaskListPlugin.create(textView.getContext()))
                .usePlugin(SyntaxHighlightPlugin.create(prism4j, codeTheme))
                .build();

        textView.setMovementMethod(LinkMovementMethod.getInstance());

        return markwon;
    }

    public void setCodeHighlightTheme() {
        this.codeTheme = Prism4jThemeDefault.create();
        if (markwon != null) {
            markwon = null;
        }
    }
}
