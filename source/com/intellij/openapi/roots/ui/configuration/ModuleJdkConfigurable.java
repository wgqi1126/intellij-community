/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleJdkUtil;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectJdksModel;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: anna
 * Date: 05-Jun-2006
 */
public class ModuleJdkConfigurable implements Disposable {
  private JdkComboBox myCbModuleJdk;
  private Sdk mySelectedModuleJdk = null;
  private ModifiableRootModel myRootModel;
  private JPanel myJdkPanel;
  private ClasspathEditor myModuleEditor;
  private ProjectJdksModel myJdksModel;
  private boolean myFreeze = false;
  private SdkModel.Listener myListener = new SdkModel.Listener() {
    public void sdkAdded(Sdk sdk) {
      reloadModel();
    }

    public void beforeSdkRemove(Sdk sdk) {
      reloadModel();
    }

    public void sdkChanged(Sdk sdk, String previousName) {
      reloadModel();
    }

    public void sdkHomeSelected(Sdk sdk, String newSdkHome) {
      reloadModel();
    }
  };

  public ModuleJdkConfigurable(ClasspathEditor moduleEditor, ModifiableRootModel model, ProjectJdksModel jdksModel) {
    myModuleEditor = moduleEditor;
    myRootModel = model;
    myJdksModel = jdksModel;
    myJdksModel.addListener(myListener);
    init();
  }

  /**
   * @return null if JDK should be inherited
   */
  @Nullable
  public Sdk getSelectedModuleJdk() {
    return myJdksModel.findSdk(mySelectedModuleJdk);
  }

  public boolean isInheritJdk() {
    return myCbModuleJdk.getSelectedItem()instanceof JdkComboBox.ProjectJdkComboBoxItem;
  }

  public JComponent createComponent() {
    return myJdkPanel;
  }

  private void reloadModel() {
    myFreeze = true;
    myCbModuleJdk.reloadModel(new JdkComboBox.ProjectJdkComboBoxItem(), myRootModel.getModule().getProject());
    reset();
    myFreeze = false;
  }

  private void init() {
    myJdkPanel = new JPanel(new GridBagLayout());
    myCbModuleJdk = new JdkComboBox(myJdksModel);
    myCbModuleJdk.insertItemAt(new JdkComboBox.ProjectJdkComboBoxItem(), 0);
    myCbModuleJdk.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myFreeze) return;
        final Sdk oldJdk = ModuleJdkUtil.getJdk(myRootModel);
        mySelectedModuleJdk = myCbModuleJdk.getSelectedJdk();
        final Sdk selectedModuleJdk = getSelectedModuleJdk();
        if (selectedModuleJdk != null) {
          ModuleJdkUtil.setJdk(myRootModel, selectedModuleJdk);
        }
        else {
          ModuleJdkUtil.inheritJdk(myRootModel);
        }
        clearCaches(oldJdk, selectedModuleJdk);
        myModuleEditor.flushChangesToModel();
      }
    });
    myJdkPanel.add(new JLabel(ProjectBundle.message("module.libraries.target.jdk.module.radio")),
                   new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(12, 6, 12, 0), 0, 0));
    myJdkPanel.add(myCbModuleJdk, new GridBagConstraints(1, 0, 1, 1, 0, 1.0,
                                                         GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                         new Insets(6, 6, 12, 0), 0, 0));
    final Project project = myRootModel.getModule().getProject();
    final JButton setUpButton = myCbModuleJdk
      .createSetupButton(project, myJdksModel, new JdkComboBox.ProjectJdkComboBoxItem(), new Condition<Sdk>(){
        public boolean value(Sdk jdk) {
          final Sdk projectJdk = myJdksModel.getProjectJdk();
          if (projectJdk == null){
            final int res =
              Messages.showYesNoDialog(myJdkPanel,
                                       ProjectBundle.message("project.roots.no.jdk.on.project.message"),
                                       ProjectBundle.message("project.roots.no.jdk.on.projecct.title"),
                                       Messages.getInformationIcon());
            if (res == DialogWrapper.OK_EXIT_CODE){
              myJdksModel.setProjectJdk(jdk);
              return true;
            }
          }
          return false;
        }
      }, true);
    myJdkPanel.add(setUpButton, new GridBagConstraints(2, 0, 1, 1, 0, 0,
                                                       GridBagConstraints.WEST, GridBagConstraints.NONE,
                                                       new Insets(0, 4, 7, 0), 0, 0));
    myCbModuleJdk.appendEditButton(myRootModel.getModule().getProject(), myJdkPanel, new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 1.0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 4, 7, 0), 0, 0) , new Computable<Sdk>() {
      @Nullable
      public Sdk compute() {
        return ModuleJdkUtil.getJdk(myRootModel);
      }
    });
  }

  private void clearCaches(final Sdk oldJdk, final Sdk selectedModuleJdk) {
    final Module module = myRootModel.getModule();
    final Project project = module.getProject();
    ModuleStructureConfigurable.getInstance(project).getContext().clearCaches(module, oldJdk, selectedModuleJdk);
  }

  public void reset() {
    myFreeze = true;
    final String jdkName = ModuleJdkUtil.getJdkName(myRootModel);
    if (jdkName != null && !ModuleJdkUtil.isJdkInherited(myRootModel)) {
      mySelectedModuleJdk = myJdksModel.findSdk(jdkName);
      if (mySelectedModuleJdk != null) {
        myCbModuleJdk.setSelectedJdk(mySelectedModuleJdk);
      } else {
        myCbModuleJdk.setInvalidJdk(jdkName);
        clearCaches(null, null);
      }
    }
    else {
      myCbModuleJdk.setSelectedJdk(null);
    }
    myFreeze = false;
  }

  public void dispose() {
    myModuleEditor = null;
    myCbModuleJdk = null;
    myJdkPanel = null;
    myJdksModel.removeListener(myListener);
  }
}
