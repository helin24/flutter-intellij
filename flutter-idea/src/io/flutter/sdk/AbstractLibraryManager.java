/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.*;
import com.intellij.openapi.util.text.StringUtil;
import io.flutter.utils.FlutterModuleUtils;
import io.flutter.utils.OpenApiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The shared code for managing a library. To use it, define a subclass that implements the required methods,
 * and calls #updateLibraryContent() with the library URLs.
 * It will need a LibraryType and LibraryProperties, which are registered in plugin.xml.
 *
 * @see FlutterPluginsLibraryManager
 */
public abstract class AbstractLibraryManager<K extends LibraryProperties> {

  @NotNull
  private final Project project;

  public AbstractLibraryManager(@NotNull Project project) {
    this.project = project;
  }

  protected void updateLibraryContent(@NotNull Set<String> contentUrls) {
    if (!FlutterModuleUtils.declaresFlutter(project)) {
      // If we have a Flutter library, remove it.
      final LibraryTable libraryTable = OpenApiUtils.getLibraryTable(project);
      if (libraryTable == null) return;
      final Library existingLibrary = getLibraryByName(getLibraryName());
      if (existingLibrary != null) {
        WriteAction.compute(() -> {
          final LibraryTable.ModifiableModel libraryTableModel = libraryTable.getModifiableModel();
          libraryTableModel.removeLibrary(existingLibrary);
          libraryTableModel.commit();
          return null;
        });
      }
      return;
    }
    updateLibraryContent(getLibraryName(), contentUrls, null);
  }

  protected void updateLibraryContent(@NotNull String name,
                                      @NotNull Set<String> contentUrls,
                                      @Nullable Set<String> sourceUrls) {
    // TODO(messick) Add support for source URLs.
    final LibraryTable libraryTable = OpenApiUtils.getLibraryTable(project);
    if (libraryTable == null) return;

    final Library existingLibrary = getLibraryByName(name);

    final Library library = existingLibrary != null
                            ? existingLibrary
                            : WriteAction.compute(() -> {
                              final LibraryTable.ModifiableModel libraryTableModel = libraryTable.getModifiableModel();
                              final Library lib = libraryTableModel.createLibrary(
                                name,
                                getLibraryKind());
                              libraryTableModel.commit();
                              return lib;
                            });

    final Set<String> existingUrls = new HashSet<>(Arrays.asList(library.getUrls(OrderRootType.CLASSES)));
    if (contentUrls.containsAll(existingUrls) && existingUrls.containsAll(contentUrls)) {
      // No changes needed.
      return;
    }

    OpenApiUtils.safeRunWriteAction(() -> {
      final LibraryEx.ModifiableModelEx model = (LibraryEx.ModifiableModelEx)library.getModifiableModel();

      final Set<String> existingCopy = new HashSet<>(existingUrls);
      existingUrls.removeAll(contentUrls);
      contentUrls.removeAll(existingCopy);

      for (String url : existingUrls) {
        model.removeRoot(url, OrderRootType.CLASSES);
      }

      for (String url : contentUrls) {
        model.addRoot(url, OrderRootType.CLASSES);
      }

      DumbService.getInstance(project).runWhenSmart(() -> {
        OpenApiUtils.safeRunWriteAction(model::commit);
      });
    });

    updateModuleLibraryDependencies(library);
  }

  protected void updateModuleLibraryDependencies(@NotNull Library library) {
    for (final Module module : OpenApiUtils.getModules(project)) {
      if (FlutterModuleUtils.declaresFlutter(module)) {
        addFlutterLibraryDependency(module, library);
      }
      else {
        removeFlutterLibraryDependency(module, library);
      }
    }
  }

  @NotNull
  protected Project getProject() {
    return project;
  }

  @NotNull
  protected abstract String getLibraryName();

  @NotNull
  protected abstract PersistentLibraryKind<K> getLibraryKind();

  protected static void addFlutterLibraryDependency(@NotNull Module module, @NotNull Library library) {
    final ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();

    try {
      for (final OrderEntry orderEntry : modifiableModel.getOrderEntries()) {
        if (orderEntry instanceof LibraryOrderEntry &&
            LibraryTablesRegistrar.PROJECT_LEVEL.equals(((LibraryOrderEntry)orderEntry).getLibraryLevel()) &&
            StringUtil.equals(library.getName(), ((LibraryOrderEntry)orderEntry).getLibraryName())) {
          return; // dependency already exists
        }
      }

      modifiableModel.addLibraryEntry(library);

      OpenApiUtils.safeInvokeAndWait(() -> WriteAction.run(modifiableModel::commit));
    }
    finally {
      if (!modifiableModel.isDisposed()) {
        modifiableModel.dispose();
      }
    }
  }

  protected static void removeFlutterLibraryDependency(@NotNull Module module, @NotNull Library library) {
    final ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();

    try {
      boolean wasFound = false;

      for (final OrderEntry orderEntry : modifiableModel.getOrderEntries()) {
        if (orderEntry instanceof LibraryOrderEntry &&
            LibraryTablesRegistrar.PROJECT_LEVEL.equals(((LibraryOrderEntry)orderEntry).getLibraryLevel()) &&
            StringUtil.equals(library.getName(), ((LibraryOrderEntry)orderEntry).getLibraryName())) {
          wasFound = true;
          modifiableModel.removeOrderEntry(orderEntry);
        }
      }

      if (wasFound) {
        OpenApiUtils.safeInvokeAndWait(() -> WriteAction.run(modifiableModel::commit));
      }
    }
    finally {
      if (!modifiableModel.isDisposed()) {
        modifiableModel.dispose();
      }
    }
  }

  @Nullable
  private Library getLibraryByName(@NotNull String name) {
    var libraryTable = OpenApiUtils.getLibraryTable(project);
    return libraryTable == null ? null : libraryTable.getLibraryByName(name);
  }
}
