/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.copybara.testing;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.Json;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;
import com.google.copybara.Options;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.folder.FolderDestinationOptions;
import com.google.copybara.folder.FolderOriginOptions;
import com.google.copybara.git.GerritOptions;
import com.google.copybara.git.GitDestinationOptions;
import com.google.copybara.git.GitMirrorOptions;
import com.google.copybara.git.GitOptions;
import com.google.copybara.git.GitOriginOptions;
import com.google.copybara.git.GithubDestinationOptions;
import com.google.copybara.git.GithubOptions;
import com.google.copybara.git.GithubPrOriginOptions;
import com.google.copybara.testing.TestingModule.TestingOptions;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.LogConsole;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.HashMap;
import java.util.Map;

/**
 * Allows building complete and sane {@link Options} instances succinctly.
 */
public class OptionsBuilder {

  public GeneralOptions general =
      new GeneralOptions(
          System.getenv(),
          Jimfs.newFileSystem(),
          /*verbose=*/ true,
          LogConsole.readWriteConsole(System.in, System.out, /*verbose*/ true),
          /* configRoot= */ null,
          /*outputRoot*/ null,
          /*reuseOutputDirs*/ true,
          /* disableReversibleCheck= */ false,
          /*force=*/ false);

  // TODO(team): Rename to folderDestination
  public FolderDestinationOptions localDestination = new FolderDestinationOptions();
  public FolderOriginOptions folderOrigin = new FolderOriginOptions();

  public GitOptions git = new GitOptions(() -> general);
  public GitOriginOptions gitOrigin = new GitOriginOptions();
  public GithubPrOriginOptions githubPrOrigin = new GithubPrOriginOptions();
  public GitDestinationOptions gitDestination = new GitDestinationOptions(() -> general, git);

  public GithubOptions github = new GithubOptions(() -> general, git) {
    @Override
    protected HttpTransport getHttpTransport() {
      throw new UnsupportedOperationException(
          "You probably have overwritten GitOptions, so you need to create this variable too");
    }
  };
  public GithubDestinationOptions githubDestination = new GithubDestinationOptions();
  public GitMirrorOptions gitMirrorOptions = new GitMirrorOptions();
  public GerritOptions gerrit = new GerritOptions();
  public WorkflowOptions workflowOptions =
      new WorkflowOptions(/*changeBaseline=*/null, /*lastRevision=*/ null,
          /*checkLastRevState=*/false);

  public TestingOptions testingOptions = new TestingOptions();

  public final OptionsBuilder setWorkdirToRealTempDir() throws IOException {
    return setWorkdirToRealTempDir(StandardSystemProperty.USER_DIR.value());
  }

  public OptionsBuilder setWorkdirToRealTempDir(String cwd) {
    general = new GeneralOptions(
        updateEnvironment(general.getEnvironment(), "PWD", cwd),
        FileSystems.getDefault(), /*verbose=*/true,
        general.console(),
        general.getConfigRoot(), general.getOutputRoot(),
        general.isNoCleanup(), general.isDisableReversibleCheck(), general.isForced());
    return this;
  }

  public OptionsBuilder setEnvironment(Map<String, String> environment) {
    general = new GeneralOptions(
        environment,
        general.getFileSystem(), general.isVerbose(), general.console(),
        general.getConfigRoot(), general.getOutputRoot(),
        general.isNoCleanup(), general.isDisableReversibleCheck(), general.isForced());
    return this;
  }

  public OptionsBuilder setOutputRootToTmpDir() throws IOException {
    general = new GeneralOptions(
        general.getEnvironment(),
        general.getFileSystem(), general.isVerbose(), general.console(),
        general.getConfigRoot(),
        // Using Files.createTempDirectory() generates paths > 255 in some tests and that causes
        // 'File name too long' exceptions in Linux
        FileSystems.getDefault().getPath(StandardSystemProperty.JAVA_IO_TMPDIR.value()),
        general.isNoCleanup(), general.isDisableReversibleCheck(), general.isForced());
    return this;
  }

  public final OptionsBuilder setConsole(Console newConsole) {
    general = new GeneralOptions(
        general.getEnvironment(), general.getFileSystem(), general.isVerbose(), newConsole,
        general.getConfigRoot(), general.getOutputRoot(),
        general.isNoCleanup(), general.isDisableReversibleCheck(), general.isForced());
    return this;
  }

  public final OptionsBuilder setHomeDir(String homeDir) {
    general = new GeneralOptions(
        updateEnvironment(general.getEnvironment(), "HOME", homeDir),
        general.getFileSystem(), general.isVerbose(), general.console(),
        general.getConfigRoot(), general.getOutputRoot(),
        general.isNoCleanup(), general.isDisableReversibleCheck(), general.isForced());
    return this;
  }

  public final OptionsBuilder setForce(boolean force) {
    general = new GeneralOptions(
        general.getEnvironment(),
        general.getFileSystem(), general.isVerbose(), general.console(),
        general.getConfigRoot(), general.getOutputRoot(),
        general.isNoCleanup(), general.isDisableReversibleCheck(), force);
    return this;
  }

  public final OptionsBuilder setLastRevision(String lastRevision) {
    workflowOptions = new WorkflowOptions(workflowOptions.getChangeBaseline(), lastRevision,
        workflowOptions.checkLastRevState);
    return this;
  }

  /**
   * Returns all options to include in the built {@link Options} instance. This can be overridden by
   * child classes, in which case it should also include the superclass' instances.
   */
  protected Iterable<Option> allOptions() {
    return ImmutableList
        .of(general, localDestination, folderOrigin, git, gitOrigin, githubPrOrigin,
            gitDestination, gitMirrorOptions, gerrit, github, githubDestination, workflowOptions,
            testingOptions);
  }

  public final Options build() {
    return new Options(ImmutableList.copyOf(allOptions()));
  }

  private static Map<String, String> updateEnvironment(
      Map<String, String> environment, String key, String value) {
    HashMap<String, String> updatedEnvironment = new HashMap<>(environment);
    updatedEnvironment.put(key, value);
    return ImmutableMap.copyOf(updatedEnvironment);
  }

  public abstract static class GithubMockHttpTransport extends MockHttpTransport {

    @Override
    public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
      MockLowLevelHttpRequest request = new MockLowLevelHttpRequest() {
        @Override
        public LowLevelHttpResponse execute() throws IOException {
          MockLowLevelHttpResponse response = (MockLowLevelHttpResponse) super.execute();
          response.setContent(getContent(method, url, this));
          return super.execute();
        }
      };
      MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
      response.setContentType(Json.MEDIA_TYPE);
      request.setResponse(response);

      return request;
    }

    protected abstract byte[] getContent(String method, String url,
        MockLowLevelHttpRequest request) throws IOException;
  }
}
