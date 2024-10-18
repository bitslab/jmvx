package org.objectweb.asm.util;

import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;

import java.io.PrintWriter;

public class StupidCheckClassAdapterHack {
    public static void printAnalyzerResult(final MethodNode method, final Analyzer<BasicValue> analyzer, final PrintWriter printWriter) {
        // Oh, you have default access?  Well, now I'm in the same package as you!
        CheckClassAdapter.printAnalyzerResult(method, analyzer, printWriter);
    }
}
