package dev.astral.api;

import dev.astral.domain.Model.Island;
import org.bukkit.event.*;

/** Fired on the server thread after terrain is built and READY has been durably committed. */
public final class IslandReadyEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Island island;
    public IslandReadyEvent(Island island) { this.island = island; }
    public Island island() { return island; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
