/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Computable;
import com.intellij.ui.content.Content;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import io.flutter.FlutterUtils;
import io.flutter.actions.ReloadAllFlutterApps;
import io.flutter.actions.ReloadFlutterApp;
import io.flutter.actions.RestartAllFlutterApps;
import io.flutter.actions.RestartFlutterApp;
import io.flutter.run.common.RunMode;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.view.FlutterViewMessages;
import io.flutter.vmService.DartVmServiceDebugProcess;
import org.dartlang.vm.service.VmService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A debug process that handles hot reloads for Flutter.
 * <p>
 * It's used for both the 'Run' and 'Debug' modes. (We apparently need a debug process even
 * when not debugging in order to support hot reload.)
 */
public class FlutterDebugProcess extends DartVmServiceDebugProcess {
  private static final @NotNull Logger LOG = Logger.getInstance(FlutterDebugProcess.class);

  private final @NotNull FlutterApp app;

  public FlutterDebugProcess(@NotNull FlutterApp app,
                             @NotNull ExecutionEnvironment executionEnvironment,
                             @NotNull XDebugSession session,
                             @NotNull ExecutionResult executionResult,
                             @NotNull DartUrlResolver dartUrlResolver,
                             @NotNull PositionMapper mapper) {
    super(executionEnvironment, session, executionResult, dartUrlResolver, app.getConnector(), mapper);
    this.app = app;
  }

  @Override
  protected void onVmConnected(@NotNull VmService vmService) {
    app.setFlutterDebugProcess(this);
    FlutterViewMessages.sendDebugActive(getSession().getProject(), app, vmService);
  }

  @Override
  public void registerAdditionalActions(@NotNull final DefaultActionGroup leftToolbar,
                                        @NotNull final DefaultActionGroup topToolbar,
                                        @NotNull final DefaultActionGroup settings) {

    if (app.getMode() != RunMode.DEBUG) {
      // Remove the debug-specific actions that aren't needed when we're not debugging.

      // Remove all but specified actions.
      final AnAction[] leftActions = leftToolbar.getChildActionsOrStubs();
      // Not all on the classpath so we resort to Strings.
      final List<String> actionClassNames = Arrays
        .asList("com.intellij.execution.actions.StopAction", "com.intellij.ui.content.tabs.PinToolwindowTabAction",
                "com.intellij.execution.ui.actions.CloseAction", "com.intellij.ide.actions.ContextHelpAction");
      for (AnAction a : leftActions) {
        if (!actionClassNames.contains(a.getClass().getName())) {
          leftToolbar.remove(a);
        }
      }

      // Remove all top actions.
      final AnAction[] topActions = topToolbar.getChildActionsOrStubs();
      for (AnAction action : topActions) {
        topToolbar.remove(action);
      }

      // Remove all settings actions.
      final AnAction[] settingsActions = settings.getChildActionsOrStubs();
      for (AnAction a : settingsActions) {
        settings.remove(a);
      }
    }

    // Add actions common to the run and debug windows.
    final Computable<Boolean> isSessionActive = () -> app.isStarted() && getVmConnected() && !getSession().isStopped();
    final Computable<Boolean> canReload = () -> app.getLaunchMode().supportsReload() && isSessionActive.compute() && !app.isReloading();
    final Computable<Boolean> debugUrlAvailable = () -> isSessionActive.compute() && app.getConnector().getBrowserUrl() != null;

    if (app.getMode() == RunMode.DEBUG) {
      topToolbar.addSeparator();
      topToolbar.addAction(new FlutterPopFrameAction());
    }

    topToolbar.addSeparator();
    topToolbar.addAction(new ReloadFlutterApp(app, canReload));
    topToolbar.addAction(new RestartFlutterApp(app, canReload));
    topToolbar.addSeparator();
    topToolbar.addAction(new OpenDevToolsAction(app, debugUrlAvailable));

    settings.addAction(new ReloadAllFlutterApps(app, canReload));
    settings.addAction(new RestartAllFlutterApps(app, canReload));
    // Don't call super since we have our own observatory action.
  }

  @Override
  public void sessionInitialized() {
    if (app.getMode() != RunMode.DEBUG) {
      suppressDebugViews(getSession().getUI());
    }
  }

  /**
   * Turn off debug-only views (variables and frames).
   */
  private static void suppressDebugViews(@Nullable RunnerLayoutUi ui) {
    if (ui == null) {
      return;
    }

    final String name = XDebuggerBundle.message("debugger.session.tab.console.content.name");
    for (Content c : ui.getContents()) {
      if (c != null && !Objects.equals(c.getTabName(), name)) {
        try {
          var application = ApplicationManager.getApplication();
          if (application != null) {
            application.invokeAndWait(() -> ui.removeContent(c, false /* dispose? */));
          }
        }
        catch (ProcessCanceledException e) {
          FlutterUtils.warn(LOG, e);
          throw e;
        }
      }
    }
  }
}
