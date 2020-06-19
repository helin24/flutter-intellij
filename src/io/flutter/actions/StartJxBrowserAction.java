/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.teamdev.jxbrowser.browser.Browser;
import com.teamdev.jxbrowser.engine.Engine;
import com.teamdev.jxbrowser.engine.EngineOptions;
import com.teamdev.jxbrowser.view.swing.BrowserView;
import io.flutter.editor.FlutterMaterialIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import static com.teamdev.jxbrowser.engine.RenderingMode.HARDWARE_ACCELERATED;


public class StartJxBrowserAction extends AnAction {
  public StartJxBrowserAction() {
    super(FlutterMaterialIcons.getIconForName("ring_volume"));
    System.setProperty("jxbrowser.license.key", "1BNDHFSC1FVTZCUP5XHXGNYJXLQFCRVXY651JETUHPTD3DMJNSGIPNZEGC33ZKKH3QSHVW");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Engine engine = Engine.newInstance(
      EngineOptions.newBuilder(HARDWARE_ACCELERATED).build());
    Browser browser = engine.newBrowser();

    SwingUtilities.invokeLater(() -> {
      BrowserView view = BrowserView.newInstance(browser);

      JFrame frame = new JFrame("Swing - Hello World");
      frame.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e) {
          // Close Engine when the application window is closed.
          engine.close();
        }
      });
      frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      frame.add(view, BorderLayout.CENTER);
      frame.setSize(500, 400);
      frame.setLocationRelativeTo(null);
      frame.setVisible(true);
    });

    browser.navigation().loadUrl("https://www.google.com");
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setVisible(true);
  }
}
