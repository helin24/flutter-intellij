/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import io.flutter.view.InspectorView;
import org.jetbrains.annotations.NotNull;

public class InspectorViewToolWindowManagerListener implements ToolWindowManagerListener {
  private boolean inspectorIsOpen;
  private Runnable onWindowOpen;
  private Runnable onWindowFirstVisible;

  public InspectorViewToolWindowManagerListener(Project project, ToolWindow toolWindow) {
    project.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, this);

    inspectorIsOpen = toolWindow.isShowStripeButton();
  }

  public void updateOnWindowOpen(Runnable onWindowOpen) {
    this.onWindowOpen = onWindowOpen;
  }

  public void updateOnWindowFirstVisible(Runnable onWindowFirstVisible) {
    this.onWindowFirstVisible = onWindowFirstVisible;
  }

  @Override
  public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
    final ToolWindow inspectorWindow = toolWindowManager.getToolWindow(InspectorView.TOOL_WINDOW_ID);
    if (inspectorWindow == null) {
      return;
    }

    final boolean newIsOpen = inspectorWindow.isShowStripeButton();
    if (newIsOpen != inspectorIsOpen) {
      inspectorIsOpen = newIsOpen;
      if (newIsOpen && onWindowOpen != null) {
        onWindowOpen.run();
      }
    }

    if (inspectorWindow.isVisible() && onWindowFirstVisible != null) {
      onWindowFirstVisible.run();
      onWindowFirstVisible = null;
    }
  }
}
