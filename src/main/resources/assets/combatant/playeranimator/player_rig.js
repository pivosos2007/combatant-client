// Default Combatant player-rig graph bootstrap.
(() => {
  const R=globalThis.CombatantPlayerRig;
  if (!R || typeof R.PlayerRigMotionGraph!=='function') throw new Error('Combatant player rig modules were not loaded');
  globalThis.CombatantPlayerAnimations=R;
  const graph=new R.PlayerRigMotionGraph(playerRig);
  globalThis.__combatant_default_player_rig_graph=graph;
  playerRig.onPose(context=>graph.apply(context));
})();
