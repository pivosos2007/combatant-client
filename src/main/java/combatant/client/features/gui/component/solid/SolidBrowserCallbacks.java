package combatant.client.features.gui.component.solid;

/** Neutral browser events. Their meaning belongs to the consumer adapter. */
public interface SolidBrowserCallbacks<N, I> {
    default void onNavigation(N value) {}
    default void onPrimaryClick(I value) {}
    default void onSecondaryClick(I value) {}
    default void onAuxiliaryClick(I value) {}
    default void onSearchResultFocused(I value) {}
    default void onSearchChanged(String value) {}
    default void onCloseDetail() {}
    default void onFooterAction() {}
}
