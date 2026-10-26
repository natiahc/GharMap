package com.paisapilot.app;

final class Engine {
    static { System.loadLibrary("paisapilot"); }
    private Engine() {}
    static native long parse(String decimal);
    static native String format(long paise);
    // Input: takehome, spending, debt, reserve contribution, goal contribution,
    // reserves, essentials, reserve months, risk basis points, cap headroom, pause.
    // Output: surplus, shortfall, longterm, risk, target, gap.
    static native long[] totals(String[] ids, int[] kinds, long[] amounts, boolean[] reviewed);
    static native long[] plan(long[] input);
    // Growth scenario: annual effective return, inflation and contribution step-up in basis points.
    // Output: future nominal assets, assets in today's money (paise, rounded down).
    static native long[] project(long initial, long monthly, int years, int returns, int inflation, int stepup);
    static String money(long paise) { return "₹" + format(paise); }
}
