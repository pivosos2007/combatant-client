package combatant.client.features.gui.component.solid;

import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeSpec;

/** Arbitrary consumer-owned detail subtree and optional shell slots. */
public record SolidDetailModel(String title,
                               String subtitle,
                               UiNodeSpec headerActions,
                               UiNodeSpec body,
                               String emptyTitle,
                               UiNodeSpec emptyState,
                               boolean open,
                               boolean empty,
                               boolean closeActionVisible) {
    public SolidDetailModel {
        title = title != null ? title : "";
        subtitle = subtitle != null ? subtitle : "";
        emptyTitle = emptyTitle != null ? emptyTitle : "";
    }

    public static SolidDetailModel shortcuts(UiNodeSpec body) {
        return new SolidDetailModel("", "", null, body, "", null, false, false, false);
    }
}
