/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

/** The decoder's preedit is authoritative even if the editor sees Chinese or no composing region. */
final class DynamicHintState {
    private String initial = "";
    private boolean engineOwned, composing;
    boolean composing() { return composing; }
    String initial() { return initial; }
    void reset() { initial = ""; engineOwned = false; composing = false; }
    void engineInitial(String value) { engineInitial(value, !value.isEmpty()); }
    void engineInitial(String value, boolean hasPreedit) { engineOwned = true; initial = value; composing = hasPreedit; }
    void clientInitial(String value) { clientInitial(value, !value.isEmpty()); }
    void clientInitial(String value, boolean hasPreedit) { if (!engineOwned) { initial = value; composing = hasPreedit; } }
}
