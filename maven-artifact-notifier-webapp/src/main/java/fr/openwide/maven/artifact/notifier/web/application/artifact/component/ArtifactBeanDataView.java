package fr.openwide.maven.artifact.notifier.web.application.artifact.component;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.IDataProvider;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.openwide.core.wicket.behavior.ClassAttributeAppender;
import fr.openwide.core.wicket.markup.html.basic.CountLabel;
import fr.openwide.core.wicket.more.markup.html.feedback.FeedbackUtils;
import fr.openwide.core.wicket.more.model.BindingModel;
import fr.openwide.core.wicket.more.util.DatePattern;
import fr.openwide.maven.artifact.notifier.core.business.artifact.model.Artifact;
import fr.openwide.maven.artifact.notifier.core.business.artifact.service.IFollowedArtifactService;
import fr.openwide.maven.artifact.notifier.core.business.search.model.ArtifactBean;
import fr.openwide.maven.artifact.notifier.core.business.search.service.IMavenCentralSearchUrlService;
import fr.openwide.maven.artifact.notifier.core.business.user.exception.AlreadyFollowedArtifactException;
import fr.openwide.maven.artifact.notifier.core.business.user.service.IUserService;
import fr.openwide.maven.artifact.notifier.core.util.binding.Binding;
import fr.openwide.maven.artifact.notifier.web.application.MavenArtifactNotifierSession;
import fr.openwide.maven.artifact.notifier.web.application.artifact.model.ArtifactLastVersionModel;
import fr.openwide.maven.artifact.notifier.web.application.artifact.model.ArtifactModel;
import fr.openwide.maven.artifact.notifier.web.application.common.component.DateLabelWithPlaceholder;
import fr.openwide.maven.artifact.notifier.web.application.common.component.LabelWithPlaceholder;

public class ArtifactBeanDataView extends DataView<ArtifactBean> {

	private static final long serialVersionUID = -4100134021146074517L;
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactBeanDataView.class);
	
	@SpringBean
	private IMavenCentralSearchUrlService mavenCentralSearchUrlService;
	
	@SpringBean
	private IUserService userService;
	
	@SpringBean
	private IFollowedArtifactService followedArtifactService;
	
	protected ArtifactBeanDataView(String id, IDataProvider<ArtifactBean> dataProvider) {
		this(id, dataProvider, Integer.MAX_VALUE);
	}
	
	protected ArtifactBeanDataView(String id, IDataProvider<ArtifactBean> dataProvider, long itemsPerPage) {
		super(id, dataProvider, itemsPerPage);
	}

	@Override
	protected void populateItem(final Item<ArtifactBean> item) {
		item.setOutputMarkupId(true);
		
		ArtifactBean artifactBean = item.getModelObject();

		final ArtifactModel artifactModel = new ArtifactModel(Model.of(item.getModelObject().getArtifactKey()));
		final ArtifactLastVersionModel artifactLastVersionModel = new ArtifactLastVersionModel(artifactModel);
		
		item.add(new ClassAttributeAppender(new LoadableDetachableModel<String>() {
			private static final long serialVersionUID = 1L;
			
			@Override
			protected String load() {
				boolean isFollowed = userService.isFollowedArtifactBean(MavenArtifactNotifierSession.get().getUser(), item.getModelObject());
				return isFollowed ? "success" : null;
			}
		}));
		
		// GroupId column
		item.add(new Label("groupId", new PropertyModel<ArtifactBean>(item.getModel(), "groupId")));
		item.add(new ExternalLink("groupLink", mavenCentralSearchUrlService.getGroupUrl(artifactBean.getGroupId())));

		// ArtifactId column
		item.add(new Label("artifactId", new PropertyModel<ArtifactBean>(item.getModel(), "artifactId")));
		item.add(new ExternalLink("artifactLink", mavenCentralSearchUrlService.getArtifactUrl(artifactBean.getGroupId(), artifactBean.getArtifactId())));
		
		// LastVersion and lastUpdateDate columns
		item.add(new Label("unfollowedArtifactPlaceholder", new ResourceModel("artifact.follow.unfollowedArtifact")) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(artifactModel.getObject() == null);
			}
		});
		item.add(new Label("synchronizationPlannedPlaceholder", new ResourceModel("artifact.follow.synchronizationPlanned")) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(artifactModel.getObject() != null && !artifactLastVersionModel.isLastVersionAvailable());
			}
		});
		
		WebMarkupContainer localContainer = new WebMarkupContainer("followedArtifact") {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(artifactLastVersionModel.isLastVersionAvailable());
			}
		};
		localContainer.add(new LabelWithPlaceholder("latestVersion", Model.of(artifactLastVersionModel.getLastVersion())));
		localContainer.add(new ExternalLink("versionLink", mavenCentralSearchUrlService.getVersionUrl(artifactBean.getGroupId(),
				artifactBean.getArtifactId(), artifactBean.getLatestVersion())));
		localContainer.add(new DateLabelWithPlaceholder("lastUpdateDate", Model.of(artifactLastVersionModel.getLastVersionUpdateDate()), DatePattern.SHORT_DATE));
		item.add(localContainer);

		// Followers count column
		item.add(new CountLabel("followersCount", "artifact.follow.dataView.followers",
				BindingModel.of(artifactModel, Binding.artifact().followersCount())) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onConfigure() {
				super.onConfigure();
				if (artifactModel.getObject() != null && artifactModel.getObject().getFollowersCount() > 0) {
					add(new AttributeModifier("class", "badge"));
				}
			}
		});
		
		// Follow column
		AjaxLink<ArtifactBean> follow = new AjaxLink<ArtifactBean>("follow", item.getModel()) {
			private static final long serialVersionUID = 1L;
			
			@Override
			public void onClick(AjaxRequestTarget target) {
				try {
					userService.followArtifactBean(MavenArtifactNotifierSession.get().getUser(), getModelObject());
					target.add(getPage());
				} catch (AlreadyFollowedArtifactException e) {
					getSession().warn(getString("artifact.follow.alreadyFollower"));
					target.add(getPage());
				} catch (Exception e) {
					LOGGER.error("Error occured while following artifact", e);
					getSession().error(getString("common.error.unexpected"));
				}
				FeedbackUtils.refreshFeedback(target, getPage());
			}
			
			@Override
			protected void onConfigure() {
				super.onConfigure();
				ArtifactBean artifactBean = getModelObject();
				setVisible(artifactBean != null && !userService.isFollowedArtifactBean(MavenArtifactNotifierSession.get().getUser(), artifactBean));
			}
		};
		item.add(follow);
		
		AjaxLink<Artifact> unfollow = new AjaxLink<Artifact>("unfollow", artifactModel) {
			private static final long serialVersionUID = 1L;
			
			@Override
			public void onClick(AjaxRequestTarget target) {
				try {
					if (!userService.unfollowArtifact(MavenArtifactNotifierSession.get().getUser(), getModelObject())) {
						getSession().warn(getString("artifact.delete.notFollowed"));
					}
					target.add(getPage());
				} catch (Exception e) {
					LOGGER.error("Error occured while unfollowing artifact", e);
					getSession().error(getString("common.error.unexpected"));
				}
				FeedbackUtils.refreshFeedback(target, getPage());
			}
			
			@Override
			protected void onConfigure() {
				super.onConfigure();
				Artifact artifact = getModelObject();
				setVisible(artifact != null && userService.isFollowedArtifact(MavenArtifactNotifierSession.get().getUser(), artifact));
			}
		};
		item.add(unfollow);
	}
	
	@Override
	protected void onConfigure() {
		setVisible(getDataProvider().size() != 0);
	}
}
