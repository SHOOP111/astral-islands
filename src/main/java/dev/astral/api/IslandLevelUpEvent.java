package dev.astral.api;

import dev.astral.domain.Model.Island;
import org.bukkit.event.*;

/** Fired on the server thread after an activity transaction crosses at least one level. */
public final class IslandLevelUpEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Island island;
    private final int previousLevel;
    public IslandLevelUpEvent(Island island, int previousLevel) { this.island = island; this.previousLevel = previousLevel; }
    public Island island() { return island; }
    public int previousLevel() { return previousLevel; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
