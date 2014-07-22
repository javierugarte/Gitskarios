package com.alorma.github.ui.fragment.detail.repo;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListPopupWindow;
import android.widget.RepositoryInfo;
import android.widget.RepositoryInfoField;
import android.widget.Toast;
import android.widget.bean.RepositoryUiInfo;

import com.alorma.github.BuildConfig;
import com.alorma.github.R;
import com.alorma.github.sdk.bean.dto.response.ListBranches;
import com.alorma.github.sdk.bean.dto.response.ListContributors;
import com.alorma.github.sdk.bean.dto.response.ListIssues;
import com.alorma.github.sdk.bean.dto.response.ListReleases;
import com.alorma.github.sdk.bean.dto.response.Repo;
import com.alorma.github.sdk.services.client.BaseClient;
import com.alorma.github.sdk.services.repo.BaseRepoClient;
import com.alorma.github.sdk.services.repo.GetRepoBranchesClient;
import com.alorma.github.sdk.services.repo.GetRepoClient;
import com.alorma.github.sdk.services.repo.GetRepoContributorsClient;
import com.alorma.github.sdk.services.repo.GetRepoIssuesClient;
import com.alorma.github.sdk.services.repo.GetRepoReleasesClient;
import com.alorma.github.ui.popup.PopUpContributors;
import com.bugsense.trace.BugSenseHandler;
import com.joanzapata.android.iconify.IconDrawable;
import com.joanzapata.android.iconify.Iconify;

import java.util.concurrent.LinkedBlockingQueue;

import fr.castorflex.android.smoothprogressbar.SmoothProgressBar;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * Created by Bernat on 17/07/2014.
 */
public class RepoDetailFragment extends Fragment implements BaseClient.OnResultCallback<Repo>, RepositoryInfo.OnRepoInfoListener {
    public static final String OWNER = "OWNER";
    public static final String REPO = "REPO";
    private RepositoryInfo infoFields;
    private String owner;
    private String repo;
    private LinkedBlockingQueue<BaseRepoClient> clients;
    private boolean reIntent = false;
    private SmoothProgressBar smoothBar;
    private ListContributors contributors;
    private ListPopupWindow currentPopup;
    private Fragment currentFragment;

    public static RepoDetailFragment newInstance(String owner, String repo) {
        Bundle bundle = new Bundle();
        bundle.putString(OWNER, owner);
        bundle.putString(REPO, repo);

        RepoDetailFragment f = new RepoDetailFragment();
        f.setArguments(bundle);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        View v = inflater.inflate(R.layout.repo_detail_fragment, null, false);

        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            owner = getArguments().getString(OWNER);
            repo = getArguments().getString(REPO);

            if (getActivity() != null && getActivity().getActionBar() != null) {
                getActivity().getActionBar().setTitle(owner + "/" + repo);
            }

            infoFields = (RepositoryInfo) view.findViewById(R.id.repoInfoFields);
            infoFields.setOnRepoInfoListener(this);

            smoothBar = (SmoothProgressBar) view.findViewById(R.id.smoothBar);

            configureClients();
            executeNextClient();
        } else {
            if (getActivity() != null) {
                getActivity().finish();
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.repo_detail_fragment, menu);

        if (menu != null) {
            MenuItem item = menu.findItem(R.id.mode);
            if (item != null) {
                IconDrawable drawable = new IconDrawable(getActivity(), Iconify.IconValue.fa_folder);
                drawable.actionBarSize();
                drawable.colorRes(R.color.white);
                item.setIcon(drawable);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        if (item.getItemId() == R.id.mode) {
            if (currentFragment != null) {
                if (currentFragment instanceof MarkdownFragment) {
                    currentFragment = FilesTreeFragment.newInstance(owner, repo);
                    IconDrawable drawable = new IconDrawable(getActivity(), Iconify.IconValue.fa_file_text);
                    drawable.actionBarSize();
                    drawable.colorRes(R.color.white);
                    item.setIcon(drawable);
                } else if (currentFragment instanceof FilesTreeFragment) {
                    currentFragment = MarkdownFragment.newInstance(owner, repo);
                    IconDrawable drawable = new IconDrawable(getActivity(), Iconify.IconValue.fa_folder);
                    drawable.actionBarSize();
                    drawable.colorRes(R.color.white);
                    item.setIcon(drawable);
                }

                showDetailFragment(currentFragment);
            }
        }

        return true;
    }

    @Override
    public void onResponseOk(Repo repo, Response r) {

        executeNextClient();

        currentFragment = MarkdownFragment.newInstance(this.owner, this.repo);
        showDetailFragment(currentFragment);
    }

    private void showDetailFragment(Fragment currentFragment) {
        if (currentFragment != null) {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.replace(R.id.filesTree, currentFragment);
            ft.commit();
        }
    }

    @Override
    public void onFail(RetrofitError error) {
        onError("REPO", error, false);
        if (!reIntent) {
            reIntent = true;
            configureClients();
            executeNextClient();
        } else {
            Toast.makeText(getActivity(), "error retrieving repository info, please contact app developer", Toast.LENGTH_SHORT).show();
            getActivity().finish();
        }
    }

    private void configureClients() {
        clients = new LinkedBlockingQueue<BaseRepoClient>();

        GetRepoClient repoClient = new GetRepoClient(getActivity(), this.owner, this.repo);
        repoClient.setOnResultCallback(this);

        clients.add(repoClient);

        GetRepoContributorsClient repoContributorsClient = new GetRepoContributorsClient(getActivity(), this.owner, this.repo);
        repoContributorsClient.setOnResultCallback(new ContributorsCallback());

        clients.add(repoContributorsClient);

        GetRepoBranchesClient repoBranchesClient = new GetRepoBranchesClient(getActivity(), this.owner, this.repo);
        repoBranchesClient.setOnResultCallback(new BranchesCallback());

        clients.add(repoBranchesClient);

        GetRepoReleasesClient repoReleasesClient = new GetRepoReleasesClient(getActivity(), this.owner, this.repo);
        repoReleasesClient.setOnResultCallback(new ReleasesCallback());

        clients.add(repoReleasesClient);

        GetRepoIssuesClient repoIssuesClient = new GetRepoIssuesClient(getActivity(), this.owner, this.repo);
        repoIssuesClient.setOnResultCallback(new IssuesCallback());

        clients.add(repoIssuesClient);

    }

    private void executeNextClient() {
        if (clients != null) {
            BaseRepoClient poll = clients.poll();
            if (poll != null) {
                poll.execute();
            } else {
                smoothBar.progressiveStop();
            }
        } else {
            smoothBar.progressiveStop();
        }
    }

    @Override
    public void onRepoInfoFieldClick(int id, RepositoryInfoField view, RepositoryUiInfo info) {
        if (id == RepositoryInfo.INFO_CONTRIBUTORS) {
            if (currentPopup != null) {
                currentPopup.dismiss();
            } else {
                currentPopup = new PopUpContributors(getActivity(), this.contributors);
                currentPopup.setAnchorView(infoFields);
                currentPopup.show();
            }
        }
    }

    private class ContributorsCallback implements BaseClient.OnResultCallback<ListContributors> {
        @Override
        public void onResponseOk(ListContributors contributors, Response r) {
            if (contributors != null) {
                RepoDetailFragment.this.contributors = contributors;
                Iconify.IconValue iconValue = Iconify.IconValue.fa_group;
                if (contributors.size() == 1) {
                    iconValue = Iconify.IconValue.fa_user;
                }
                infoFields.addRepoInfoFieldNum(RepositoryInfo.INFO_CONTRIBUTORS, contributors.size());
                infoFields.addRepoInfoFieldIcon(RepositoryInfo.INFO_CONTRIBUTORS, iconValue);
            }
            executeNextClient();
        }

        @Override
        public void onFail(RetrofitError error) {
            onError("CONTRIBUTORS", error, true);
        }
    }

    private class BranchesCallback implements BaseClient.OnResultCallback<ListBranches> {
        @Override
        public void onResponseOk(ListBranches branches, Response r) {
            if (branches != null) {
                infoFields.addRepoInfoFieldNum(RepositoryInfo.INFO_BRANCHES, branches.size());
            }
            executeNextClient();
        }

        @Override
        public void onFail(RetrofitError error) {
            onError("BRANCHES", error, true);
        }
    }

    private class ReleasesCallback implements BaseClient.OnResultCallback<ListReleases> {
        @Override
        public void onResponseOk(ListReleases releases, Response r) {
            if (releases != null) {
                infoFields.addRepoInfoFieldNum(RepositoryInfo.INFO_RELEASES, releases.size());
            }
            executeNextClient();
        }

        @Override
        public void onFail(RetrofitError error) {
            onError("RELEASES", error, true);
        }
    }

    private class IssuesCallback implements BaseClient.OnResultCallback<ListIssues> {
        @Override
        public void onResponseOk(ListIssues issues, Response r) {
            if (issues != null) {
                infoFields.addRepoInfoFieldNum(RepositoryInfo.INFO_ISSUES, issues.size());
            }
            executeNextClient();

            smoothBar.progressiveStop();
        }

        @Override
        public void onFail(RetrofitError error) {
            onError("ISSUES", error, true);
        }
    }

    private void onError(String tag, RetrofitError error, boolean executeNext) {
        Log.e(tag, "Error", error);
        if (BuildConfig.DEBUG) {
            Toast.makeText(getActivity(), "Error: " + tag, Toast.LENGTH_SHORT).show();
        }
        if (executeNext) {
            executeNextClient();
        } else {
            smoothBar.progressiveStop();
        }

        if (error != null && error.getMessage() != null) {
            BugSenseHandler.addCrashExtraData(tag, error.getMessage());
            BugSenseHandler.flush(getActivity());
        }
    }
}