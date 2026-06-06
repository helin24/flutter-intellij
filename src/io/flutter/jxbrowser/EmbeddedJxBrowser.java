/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.jxbrowser;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.ContentManager;
import com.teamdev.jxbrowser.browser.Browser;
import com.teamdev.jxbrowser.browser.UnsupportedRenderingModeException;
import com.teamdev.jxbrowser.browser.callback.AlertCallback;
import com.teamdev.jxbrowser.browser.callback.ConfirmCallback;
import com.teamdev.jxbrowser.browser.callback.input.PressKeyCallback;
import com.teamdev.jxbrowser.browser.event.ConsoleMessageReceived;
import com.teamdev.jxbrowser.engine.Engine;
import com.teamdev.jxbrowser.js.ConsoleMessage;
import com.teamdev.jxbrowser.permission.PermissionType;
import com.teamdev.jxbrowser.permission.callback.RequestPermissionCallback;
import com.teamdev.jxbrowser.ui.KeyCode;
import com.teamdev.jxbrowser.ui.event.KeyPressed;
import com.teamdev.jxbrowser.view.swing.BrowserView;
import com.teamdev.jxbrowser.view.swing.callback.DefaultAlertCallback;
import com.teamdev.jxbrowser.view.swing.callback.DefaultConfirmCallback;
import io.flutter.logging.PluginLogger;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.AsyncUtils;
import io.flutter.utils.JxBrowserUtils;
import io.flutter.utils.OpenApiUtils;
import io.flutter.utils.ZoomLevelSelector;
import io.flutter.view.EmbeddedBrowser;
import io.flutter.view.EmbeddedTab;
import io.flutter.utils.LabelInput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.teamdev.jxbrowser.zoom.Zoom;
import com.teamdev.jxbrowser.zoom.ZoomLevel;
import com.intellij.ide.ui.UISettingsUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import com.intellij.util.SmartList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.openapi.ui.VerticalFlowLayout;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


class EmbeddedJxBrowserTab implements EmbeddedTab {
  private final Engine engine;
  private Browser browser;
  private Zoom zoom;
  private final ZoomLevelSelector zoomSelector = new ZoomLevelSelector();
  private static final @NotNull Logger LOG = PluginLogger.createLogger(EmbeddedJxBrowserTab.class);

  public EmbeddedJxBrowserTab(Engine engine) {
    this.engine = engine;

    try {
      // Support copying text in the embedded browser to the clipboard. The following was copied from:
      // https://teamdev.com/jxbrowser/docs/guides/clipboard/#necessary-permissions
      this.engine.permissions().set(RequestPermissionCallback.class, (params, tell) -> {
        var type = params.permissionType();
        if (type == PermissionType.CLIPBOARD_READ_WRITE
            || type == PermissionType.CLIPBOARD_SANITIZED_WRITE) {
          tell.grant();
        } else {
          tell.deny();
        }
      });

      this.browser = engine.newBrowser();
      this.zoom = this.browser.zoom();
      this.browser.settings().enableTransparentBackground();
      this.browser.on(ConsoleMessageReceived.class, event -> {
        final ConsoleMessage consoleMessage = event.consoleMessage();
        LOG.info("Browser message(" + consoleMessage.level().name() + "): " + consoleMessage.message());
      });
    }
    catch (UnsupportedRenderingModeException ex) {
      // Skip using a transparent background if an exception is thrown.
    }
    catch (Exception | Error ex) {
      if (FlutterSettings.getInstance().isFilePathLoggingEnabled()) {
        LOG.info(ex);
      } else {
        LOG.info("Exception when creating a new browser instance: " + ex.getMessage());
      }
    }
  }

  @Override
  public void loadUrl(String url) {
    this.browser.navigation().loadUrl(url);
  }

  @Override
  public void close() {
    OpenApiUtils.safeExecuteOnPooledThread(() -> {
      try {
        this.browser.close();
      }
      catch (Exception ex) {
        if (FlutterSettings.getInstance().isFilePathLoggingEnabled()) {
          LOG.info(ex);
        } else {
          LOG.info("Exception when closing JX Browser instance: " + ex.getMessage());
        }
      }
    });
  }

  @Override
  public void matchIdeZoom() {
    if (this.zoom != null) {
      final ZoomLevel zoomLevel = zoomSelector.getClosestZoomLevel(getIdeZoomPercent());
      this.zoom.level(zoomLevel);
    }
  }

  private int getIdeZoomPercent() {
    final UISettingsUtils uiSettingsUtils = UISettingsUtils.getInstance();
    final float ideScale = uiSettingsUtils.getCurrentIdeScale();
    return Math.round(ideScale * 100);
  }

  @Override
  public JComponent getTabComponent(ContentManager contentManager) {
    // Creating Swing component for rendering web content
    // loaded in the given Browser instance.
    final BrowserView view = BrowserView.newInstance(browser);
    view.setPreferredSize(new Dimension(contentManager.getComponent().getWidth(), contentManager.getComponent().getHeight()));

    // DevTools may show a confirm dialog to use a fallback version.
    browser.set(ConfirmCallback.class, new DefaultConfirmCallback(view));
    browser.set(AlertCallback.class, new DefaultAlertCallback(view));

    // This is for pulling up Chrome inspector for debugging purposes.
    browser.set(PressKeyCallback.class, params -> {
      KeyPressed keyEvent = params.event();
      boolean keyCodeC = keyEvent.keyCode() == KeyCode.KEY_CODE_J;
      boolean controlDown = keyEvent.keyModifiers().isControlDown();
      if (controlDown && keyCodeC) {
        browser.devTools().show();
      }
      return PressKeyCallback.Response.proceed();
    });

    return view;
  }
}

class LazyEmbeddedJxBrowserTab implements EmbeddedTab {
  private final @NotNull ContentManager contentManager;
  private final @NotNull Project project;
  private final @NotNull EmbeddedJxBrowser embeddedBrowser;
  private EmbeddedTab delegate;
  private String pendingUrl;
  private final JPanel panel = new JPanel(new BorderLayout());
  private final JxBrowserUtils jxBrowserUtils = new JxBrowserUtils();

  public LazyEmbeddedJxBrowserTab(@NotNull EmbeddedJxBrowser embeddedBrowser,
                                  @NotNull Project project,
                                  @NotNull ContentManager contentManager,
                                  @NotNull CompletableFuture<JxBrowserStatus> installation) {
    this.embeddedBrowser = embeddedBrowser;
    this.project = project;
    this.contentManager = contentManager;

    JxBrowserStatus status = JxBrowserManager.getInstance().getStatus();
    if (status == JxBrowserStatus.INSTALLATION_FAILED) {
      setupFailedUI(JxBrowserManager.getInstance().getLatestFailureReason());
    } else {
      setupInstallingUI();
      listenToInstallation(installation);
    }
  }

  private void listenToInstallation(CompletableFuture<JxBrowserStatus> future) {
    AsyncUtils.whenCompleteUiThread(future, (status, throwable) -> {
      if (throwable != null) {
        setupFailedUI(new InstallationFailedReason(FailureType.CLASS_LOAD_FAILED, throwable.getMessage()));
      } else if (status == JxBrowserStatus.INSTALLED) {
        Engine engine = EmbeddedBrowserEngine.getInstance().getEngine();
        if (engine != null) {
          delegate = new EmbeddedJxBrowserTab(engine);
          if (pendingUrl != null) {
            delegate.loadUrl(pendingUrl);
          }
          panel.removeAll();
          panel.add(delegate.getTabComponent(contentManager), BorderLayout.CENTER);
          panel.revalidate();
          panel.repaint();
          delegate.matchIdeZoom();
        } else {
          setupFailedUI(new InstallationFailedReason(FailureType.CLASS_NOT_FOUND, "Engine is null"));
        }
      } else if (status == JxBrowserStatus.INSTALLATION_FAILED) {
        setupFailedUI(JxBrowserManager.getInstance().getLatestFailureReason());
      }
    });
  }

  private void setupInstallingUI() {
    panel.removeAll();
    final JPanel labelsPanel = new JPanel(new GridLayout(0, 1));

    final JLabel descriptionLabel = new JLabel("<html>" + EmbeddedJxBrowser.INSTALLATION_IN_PROGRESS_LABEL + "</html>");
    descriptionLabel.setBorder(JBUI.Borders.empty(5));
    descriptionLabel.setHorizontalAlignment(SwingConstants.CENTER);
    labelsPanel.add(descriptionLabel);

    final LinkLabel<String> linkLabel = new LinkLabel<>("<html>Open DevTools in the browser?</html>", null);
    linkLabel.setBorder(JBUI.Borders.empty(5));
    linkLabel.setListener((a, b) -> openInBrowser(), null);
    linkLabel.setHorizontalAlignment(SwingConstants.CENTER);
    labelsPanel.add(linkLabel);

    final JPanel center = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.MIDDLE));
    center.add(labelsPanel);

    panel.add(center, BorderLayout.CENTER);
    panel.revalidate();
    panel.repaint();
  }

  private void setupFailedUI(InstallationFailedReason failedReason) {
    panel.removeAll();
    final JPanel labelsPanel = new JPanel(new GridLayout(0, 1));

    if (!jxBrowserUtils.licenseIsSet()) {
      labelsPanel.add(createCentredLabel("The JxBrowser license could not be found."));
    }
    else if (failedReason != null && Objects.equals(failedReason.failureType, FailureType.SYSTEM_INCOMPATIBLE)) {
      labelsPanel.add(createCentredLabel(failedReason.detail));
    }
    else {
      labelsPanel.add(createCentredLabel("JxBrowser installation failed."));

      final LinkLabel<String> retryLabel = new LinkLabel<>("<html>Retry installation?</html>", null);
      retryLabel.setBorder(JBUI.Borders.empty(5));
      retryLabel.setListener((linkLabel, data) -> {
        JxBrowserManager.getInstance().retryFromFailed(project);
        setupInstallingUI();
        listenToInstallation(JxBrowserManager.installation);
      }, null);
      retryLabel.setHorizontalAlignment(SwingConstants.CENTER);
      labelsPanel.add(retryLabel);
    }

    final LinkLabel<String> openBrowserLabel = new LinkLabel<>("<html>Open DevTools in the browser?</html>", null);
    openBrowserLabel.setBorder(JBUI.Borders.empty(5));
    openBrowserLabel.setListener((a, b) -> openInBrowser(), null);
    openBrowserLabel.setHorizontalAlignment(SwingConstants.CENTER);
    labelsPanel.add(openBrowserLabel);

    final JPanel center = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.MIDDLE));
    center.add(labelsPanel);

    panel.add(center, BorderLayout.CENTER);
    panel.revalidate();
    panel.repaint();
  }

  private JLabel createCentredLabel(String text) {
    final JLabel label = new JLabel("<html>" + text + "</html>");
    label.setBorder(JBUI.Borders.empty(5));
    label.setHorizontalAlignment(SwingConstants.CENTER);
    return label;
  }

  private void openInBrowser() {
    if (pendingUrl == null) {
      return;
    }
    BrowserLauncher.getInstance().browse(pendingUrl, null);
  }

  @Override
  public void loadUrl(String url) {
    if (delegate != null) {
      delegate.loadUrl(url);
    } else {
      pendingUrl = url;
    }
  }

  @Override
  public void close() {
    if (delegate != null) {
      delegate.close();
    }
  }

  @Override
  public void matchIdeZoom() {
    if (delegate != null) {
      delegate.matchIdeZoom();
    }
  }

  @Override
  public JComponent getTabComponent(ContentManager contentManager) {
    return panel;
  }
}

public class EmbeddedJxBrowser extends EmbeddedBrowser {
  private static final @NotNull Logger LOG = PluginLogger.createLogger(JxBrowserManager.class);
  static final String INSTALLATION_IN_PROGRESS_LABEL = "Installing JxBrowser...";
  private static final String INSTALLATION_TIMED_OUT_LABEL =
    "Waiting for JxBrowser installation timed out. Restart your IDE to try again.";
  private static final String INSTALLATION_WAIT_FAILED = "The JxBrowser installation failed unexpectedly. Restart your IDE to try again.";
  private static final int INSTALLATION_WAIT_LIMIT_SECONDS = 30;

  @NotNull
  private final AtomicReference<Engine> engineRef = new AtomicReference<>(null);

  private final Project project;

  private final JxBrowserManager jxBrowserManager;
  private final JxBrowserUtils jxBrowserUtils;

  @NotNull
  public static EmbeddedJxBrowser getInstance(@NotNull Project project) {
    return Objects.requireNonNull(project.getService(EmbeddedJxBrowser.class));
  }

  private EmbeddedJxBrowser(@NotNull Project project) {
    super(project);
    this.project = project;

    this.jxBrowserManager = JxBrowserManager.getInstance();
    this.jxBrowserUtils = new JxBrowserUtils();
    final JxBrowserStatus jxBrowserStatus = jxBrowserManager.getStatus();

    if (jxBrowserStatus.equals(JxBrowserStatus.NOT_INSTALLED) || jxBrowserStatus.equals(JxBrowserStatus.INSTALLATION_SKIPPED)) {
      jxBrowserManager.setUp(project.getName());
    }

    System.setProperty("jxbrowser.force.dpi.awareness", "1.0");
    System.setProperty("jxbrowser.logging.level", "DEBUG");
    System.setProperty("jxbrowser.logging.file", PathManager.getLogPath() + File.separatorChar + "jxbrowser.log");
    if (FlutterSettings.getInstance().isVerboseLogging()) {
      System.setProperty("jxbrowser.logging.level", "ALL");
    }

    JxBrowserManager.installation.thenAccept((JxBrowserStatus status) -> {
      if (Objects.equals(status, JxBrowserStatus.INSTALLED)) {
        engineRef.compareAndSet(null, EmbeddedBrowserEngine.getInstance().getEngine());
      }
    });
  }

  @Override
  public @NotNull Logger logger() {
    return LOG;
  }

  @Override
  public @Nullable EmbeddedTab openEmbeddedTab(@NotNull ContentManager contentManager) {
    manageJxBrowserDownload();
    final Engine engine = engineRef.get();
    if (engine == null) {
      return new LazyEmbeddedJxBrowserTab(this, project, contentManager, JxBrowserManager.installation);
    }
    return new EmbeddedJxBrowserTab(engine);
  }

  private @NotNull String jxBrowserErrorMessage() {
    final String defaultError = "JX Browser engine failed to start";
    if (jxBrowserManager == null) {
      return defaultError;
    }
    switch (jxBrowserManager.getStatus()) {
      case NOT_INSTALLED:
        return "JX Browser is not installed";
      case INSTALLATION_IN_PROGRESS:
        return "JX Browser installation in progress";
      case INSTALLATION_SKIPPED:
        return "JX Browser installation skipped";
      case INSTALLATION_FAILED:
        final InstallationFailedReason failedReason = jxBrowserManager.getLatestFailureReason();
        final @Nullable String errorFromFailedMessage = jxBrowserErrorFromFailedReason(failedReason);
        return errorFromFailedMessage != null ? errorFromFailedMessage : defaultError;
      default:
        return defaultError;
    }
  }

  private @Nullable String jxBrowserErrorFromFailedReason(@Nullable InstallationFailedReason failedReason) {
    if (failedReason == null) return null;
    final FailureType failureType = failedReason.failureType;
    return switch (failureType) {
      case SYSTEM_INCOMPATIBLE -> "System is incompatible with JX Browser";
      case FILE_DOWNLOAD_FAILED -> "JX Browser file download failed";
      case MISSING_KEY -> "JX Browser license key is missing";
      case DIRECTORY_CREATION_FAILED -> "JX Browser directory creation failed";
      case MISSING_PLATFORM_FILES -> "JX Browser platform files are missing";
      case CLASS_LOAD_FAILED -> "JX Browser class load failed";
      case CLASS_NOT_FOUND -> "JX Browser class not found";
    };
  }

  private void manageJxBrowserDownload() {
    final JxBrowserStatus jxBrowserStatus = jxBrowserManager.getStatus();

    if (jxBrowserStatus.equals(JxBrowserStatus.INSTALLED)) {
      return;
    }
    else if (jxBrowserStatus.equals(JxBrowserStatus.NOT_INSTALLED) || jxBrowserStatus.equals(JxBrowserStatus.INSTALLATION_SKIPPED)) {
      jxBrowserManager.setUp(project.getName());
    }
  }
}
