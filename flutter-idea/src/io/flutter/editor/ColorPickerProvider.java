/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.ui.popup.Balloon;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public interface ColorPickerProvider {
  interface ColorListener {
    void colorChanged(@Nullable Color var1, Object var2);
  }

  void show(Color initialColor,
            JComponent component,
            Point offset,
            Balloon.Position position,
            ColorListener colorListener,
            Runnable onCancel,
            Runnable onOk);

  void dispose();
}
