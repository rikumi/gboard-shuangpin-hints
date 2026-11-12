/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

/** The decoder's preedit is authoritative even if the editor sees Chinese or no composing region. */
final class DynamicHintState {
    private String initial = "";
    private boolean engineOwned;
    String initial() { return initial; }
    void reset() { initial = ""; engineOwned = false; }
    void engineInitial(String value) { engineOwned = true; initial = value; }
    void clientInitial(String value) { if (!engineOwned) initial = value; }
}
