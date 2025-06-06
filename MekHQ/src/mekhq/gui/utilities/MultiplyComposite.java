/*
 * Copyright (C) 2016-2025 The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL),
 * version 3 or (at your option) any later version,
 * as published by the Free Software Foundation.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * A copy of the GPL should have been included with this project;
 * if not, see <https://www.gnu.org/licenses/>.
 *
 * NOTICE: The MegaMek organization is a non-profit group of volunteers
 * creating free software for the BattleTech community.
 *
 * MechWarrior, BattleMech, `Mech and AeroTech are registered trademarks
 * of The Topps Company, Inc. All Rights Reserved.
 *
 * Catalyst Game Labs and the Catalyst Game Labs logo are trademarks of
 * InMediaRes Productions, LLC.
 */
package mekhq.gui.utilities;

import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.RenderingHints;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.stream.IntStream;

public class MultiplyComposite implements Composite {
    public static final Composite INSTANCE = new MultiplyComposite();

    @Override
    public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
        return new MultiplyCompositeContext();
    }

    private static class MultiplyCompositeContext implements CompositeContext {
        private void validateRaster(Raster r) {
            if (r.getSampleModel().getDataType() != DataBuffer.TYPE_INT) {
                throw new IllegalArgumentException("Raster sample type has to be integer");
            }
        }

        private int component(int argb, int shift) {
            return (argb >> shift) & 0xff;
        }

        @Override
        public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
            validateRaster(src);
            validateRaster(dstIn);
            validateRaster(dstOut);

            final int width = Math.min(src.getWidth(), dstIn.getWidth());
            final int height = Math.min(src.getHeight(), dstIn.getHeight());
            final int[] srcRow = new int[width];
            final int[] dstRow = new int[width];
            IntStream.range(0, height).forEach(y -> {
                src.getDataElements(0, y, width, 1, srcRow);
                dstIn.getDataElements(0, y, width, 1, dstRow);
                IntStream.range(0, width).forEach(x -> {
                    final int a = Math.min(255, component(srcRow[x], 24) + component(dstRow[x], 24));
                    final int r = component(srcRow[x], 16) * component(dstRow[x], 16) / 255;
                    final int g = component(srcRow[x], 8) * component(dstRow[x], 8) / 255;
                    final int b = component(srcRow[x], 0) * component(dstRow[x], 0) / 255;
                    dstRow[x] = (a << 24) | (r << 16) | (g << 8) | b;
                });
                dstOut.setDataElements(0, y, width, 1, dstRow);
            });
        }

        @Override
        public void dispose() {}
    }
}
