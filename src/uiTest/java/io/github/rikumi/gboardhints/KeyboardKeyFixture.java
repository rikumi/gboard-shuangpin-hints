/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;

import android.content.Context;
import android.content.res.Resources;
import android.widget.FrameLayout;

/** A key with a stable resource position, like native Gboard layout entries. */
final class KeyboardKeyFixture extends FrameLayout {
    String position = "key_pos_0_0";
    private Resources resources;
    KeyboardKeyFixture(Context context) {
        super(context);
        Resources original = super.getResources();
        resources = new Resources(original.getAssets(), original.getDisplayMetrics(), original.getConfiguration()) {
            @Override public String getResourceEntryName(int id) throws NotFoundException {
                return id == android.R.id.button1 ? position : original.getResourceEntryName(id);
            }
        };
        setId(android.R.id.button1);
    }
    @Override public Resources getResources() { return resources == null ? super.getResources() : resources; }
}
