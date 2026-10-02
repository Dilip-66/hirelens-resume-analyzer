package com.resumerag.repository;

import com.resumerag.model.AnalysisMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Matches for a set of requirement rows, in one query. */
public interface AnalysisMatchRepository extends JpaRepository<AnalysisMatch, UUID> {

    /**
     * Every match belonging to the given requirements.
     *
     * <p>Batched on purpose: resolving a report's matches one requirement at a time
     * would be one query per requirement, so a thirty-requirement analysis would
     * run thirty-one queries to render one page.
     */
    List<AnalysisMatch> findByAnalysisRequirementIdInOrderByIdAsc(List<UUID> requirementIds);
}
