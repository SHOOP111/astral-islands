package dev.astral.storage;

import java.util.List;
import dev.astral.domain.Universe;
import dev.astral.domain.Model.Audit;

public interface Store extends AutoCloseable {
    Universe load() throws Exception;
    void commit(Universe before, Universe after, Audit audit) throws Exception;
    List<Audit> audit(int limit) throws Exception;
    @Override void close() throws Exception;
}
