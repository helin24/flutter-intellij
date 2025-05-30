/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.google.common.base.Joiner;
import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import io.flutter.FlutterBundle;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.LaunchState;
import io.flutter.run.SdkFields;
import io.flutter.run.SdkRunConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class RunFlutterAction extends AnAction {
  private final @NotNull String myDetailedTextKey;
  private final @NotNull FlutterLaunchMode myLaunchMode;
  private final @NotNull String myExecutorId;

  public RunFlutterAction(
    @NotNull String detailedTextKey,
    @NotNull FlutterLaunchMode launchMode,
    @NotNull String executorId) {
    myDetailedTextKey = detailedTextKey;
    myLaunchMode = launchMode;
    myExecutorId = executorId;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    // NOTE: When making changes here, consider making similar changes to ConnectAndroidDebuggerAction.
    final RunnerAndConfigurationSettings settings = getRunConfigSettings(e);
    if (settings == null) {
      return;
    }

    final RunConfiguration configuration = settings.getConfiguration();
    if (!(configuration instanceof SdkRunConfig)) {
      // Action is disabled; shouldn't happen.
      return;
    }

    final SdkRunConfig sdkRunConfig = (SdkRunConfig)configuration.clone();
    assert sdkRunConfig != null;
    final SdkFields fields = sdkRunConfig.getFields();
    final String additionalArgs = fields.getAdditionalArgs();

    String flavorArg = null;
    if (fields.getBuildFlavor() != null) {
      flavorArg = "--flavor=" + fields.getBuildFlavor();
    }

    final List<String> args = new ArrayList<>();
    if (additionalArgs != null) {
      args.add(additionalArgs);
    }
    if (flavorArg != null) {
      args.add(flavorArg);
    }
    if (!args.isEmpty()) {
      fields.setAdditionalArgs(Joiner.on(" ").join(args));
    }

    final Executor executor = getExecutor(myExecutorId);
    if (executor == null) {
      return;
    }

    final ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.create(executor, sdkRunConfig);

    final ExecutionEnvironment env;

    try {
      env = builder.activeTarget().dataContext(e.getDataContext()).build();
    }
    catch (IllegalStateException ex) {
      // We're seeing IllegalStateExceptions from here (#4067 - "Runner must be specified"), and are not sure of
      // the reason why. This adds a bit more diagnostics to the exception to help us determine what's going on.
      throw new IllegalStateException(
        ex.getMessage() + " (" + myExecutorId + "/" + myLaunchMode + "/" + getClass().getSimpleName() + ")");
    }

    FlutterLaunchMode.addToEnvironment(env, myLaunchMode);

    ProgramRunnerUtil.executeConfiguration(env, false, true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    // Text.
    final String config = getSelectedRunConfig(e);
    final String message =
      config != null ? FlutterBundle.message(myDetailedTextKey, config) : FlutterBundle.message("app.profile.action.text");
    e.getPresentation().setText(message);

    // Enablement.
    e.getPresentation().setEnabled(shouldEnable(e));
  }

  private static boolean shouldEnable(@Nullable AnActionEvent e) {
    final RunnerAndConfigurationSettings settings = getRunConfigSettings(e);
    final RunConfiguration config = settings == null ? null : settings.getConfiguration();
    return config instanceof SdkRunConfig && LaunchState.getRunningAppProcess((SdkRunConfig)config) == null;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Nullable
  protected static String getSelectedRunConfig(@Nullable AnActionEvent e) {
    final RunnerAndConfigurationSettings settings = getRunConfigSettings(e);
    if (settings != null) {
      return settings.getConfiguration().getName();
    }
    return null;
  }

  @Nullable
  public static RunnerAndConfigurationSettings getRunConfigSettings(@Nullable AnActionEvent event) {
    if (event == null) {
      return null;
    }

    final Project project = event.getProject();
    if (project == null) {
      return null;
    }

    return RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
  }

  @Nullable
  public static Executor getExecutor(@NotNull String executorId) {
    for (Executor executor : Objects.requireNonNull(Executor.EXECUTOR_EXTENSION_NAME.getExtensions())) {
      assert executor != null;
      if (executorId.equals(executor.getId())) {
        return executor;
      }
    }

    return null;
  }
}
