/*
* MegaMek - Copyright (C) 2020 - The MegaMek Team
*
* This program is free software; you can redistribute it and/or modify it under
* the terms of the GNU General Public License as published by the Free Software
* Foundation; either version 2 of the License, or (at your option) any later
* version.
*
* This program is distributed in the hope that it will be useful, but WITHOUT
* ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
* FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
* details.
*/
package mekhq.campaign.stratcon;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import megamek.common.annotations.Nullable;
import mekhq.MekHQ;
import mekhq.adapter.DateAdapter;
import mekhq.campaign.Campaign;
import mekhq.campaign.event.DeploymentChangedEvent;
import mekhq.campaign.force.Force;
import mekhq.campaign.mission.AtBDynamicScenario;
import mekhq.campaign.mission.ScenarioForceTemplate;
import mekhq.campaign.mission.ScenarioTemplate;
import mekhq.campaign.unit.Unit;

import java.time.LocalDate;
import java.util.*;

/**
 * Class that handles scenario metadata and interaction at the StratCon level
 * @author NickAragua
 */
public class StratconScenario implements IStratconDisplayable {
    /**
     * Represents the possible states of a Stratcon scenario
     */
    public enum ScenarioState {
        NONEXISTENT,
        UNRESOLVED,
        PRIMARY_FORCES_COMMITTED,
        REINFORCEMENTS_COMMITTED,
        COMPLETED,
        IGNORED,
        DEFEATED;

        private final static Map<ScenarioState, String> scenarioStateNames;

        static {
            scenarioStateNames = new HashMap<>();
            scenarioStateNames.put(ScenarioState.NONEXISTENT, "Shouldn't be seen");
            scenarioStateNames.put(ScenarioState.UNRESOLVED, "Unresolved");
            scenarioStateNames.put(ScenarioState.PRIMARY_FORCES_COMMITTED, "Primary forces committed");
            scenarioStateNames.put(ScenarioState.COMPLETED, "Victory");
            scenarioStateNames.put(ScenarioState.IGNORED, "Ignored");
            scenarioStateNames.put(ScenarioState.DEFEATED, "Defeat");
        }

        public String getScenarioStateName() {
            return scenarioStateNames.get(this);
        }
    }

    private AtBDynamicScenario backingScenario;

    private int backingScenarioID;
    private ScenarioState currentState = ScenarioState.UNRESOLVED;
    private int requiredPlayerLances;
    private boolean requiredScenario;
    private boolean isStrategicObjective;
    private LocalDate deploymentDate;
    private LocalDate actionDate;
    private LocalDate returnDate;
    private StratconCoords coords;
    private int numDefensivePoints;
    private boolean ignoreForceAutoAssignment;
    private int leadershipPointsUsed;
    private Set<Integer> failedReinforcements = new HashSet<>();
    private ArrayList<Integer> primaryForceIDs = new ArrayList<>();

    /**
     * Add a force to the backing scenario. Do our best to add the force as a "primary" force, as defined in the scenario template.
     * @param forceID ID of the force to add.
     */
    public void addPrimaryForce(int forceID) {
        backingScenario.addForce(forceID, ScenarioForceTemplate.PRIMARY_FORCE_TEMPLATE_ID);
        primaryForceIDs.add(forceID);
    }

    /**
     * Add a force to the backing scenario, trying to associate it with the given template.
     * Does some scenario and force house-keeping, fires a deployment changed event.
     */
    public void addForce(Force force, String templateID, Campaign campaign) {
        if (!getBackingScenario().getForceIDs().contains(force.getId())) {
            backingScenario.addForce(force.getId(), templateID);
            force.setScenarioId(getBackingScenarioID(), campaign);
            MekHQ.triggerEvent(new DeploymentChangedEvent(force, getBackingScenario()));
        }
    }

    /**
     * Add an individual unit to the backing scenario, trying to associate it with the given template.
     * Performs house keeping on the unit and scenario and invokes a deployment changed event.
     */
    public void addUnit(Unit unit, String templateID, boolean useLeadership) {
        if (!backingScenario.containsPlayerUnit(unit.getId())) {
            backingScenario.addUnit(unit.getId(), templateID);
            unit.setScenarioId(getBackingScenarioID());

            if (useLeadership) {
                int baseBattleValue = unit.getEntity().calculateBattleValue(true, true);
                leadershipPointsUsed += baseBattleValue;
            }

            MekHQ.triggerEvent(new DeploymentChangedEvent(unit, getBackingScenario()));
        }
    }

    public List<Integer> getAssignedForces() {
        return backingScenario.getForceIDs();
    }

    /**
     * These are all of the force IDs that have been matched up to a template
     * Note: since there's a default Reinforcements template, this is all forces
     * that have been assigned to this scenario
     */
    public List<Integer> getPlayerTemplateForceIDs() {
        return backingScenario.getPlayerTemplateForceIDs();
    }

    /**
     * These are all the "primary" force IDs, meaning forces that have been used
     * by the scenario to drive the generation of the OpFor.
     */
    @XmlElementWrapper(name = "primaryForceIDs")
    @XmlElement(name = "primaryForceID")
    public ArrayList<Integer> getPrimaryForceIDs() {
        // <50.02 compatibility handler
        if (primaryForceIDs == null) {
            primaryForceIDs = new ArrayList<>();
        }

        return primaryForceIDs;
    }

    public void setPrimaryForceIDs(ArrayList<Integer> primaryForceIDs) {
        // <50.02 compatibility handler
        if (primaryForceIDs == null) {
            primaryForceIDs = new ArrayList<>();
        }

        this.primaryForceIDs = primaryForceIDs;
    }

    /**
     * This convenience method sets the scenario's current state to PRIMARY_FORCES_COMMITTED
     * and fixes the forces that were assigned to this scenario prior as "primary".
     */
    public void commitPrimaryForces() {
        currentState = ScenarioState.PRIMARY_FORCES_COMMITTED;
        getPrimaryForceIDs().clear();

        for (int forceID : backingScenario.getPlayerTemplateForceIDs()) {
            getPrimaryForceIDs().add(forceID);
        }
    }

    public ScenarioState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(ScenarioState state) {
        currentState = state;
    }

    @Override
    public String getInfo() {
        return getInfo(null);
    }

    public String getInfo(@Nullable Campaign campaign) {
        StringBuilder stateBuilder = new StringBuilder();

        if (isStrategicObjective()) {
            stateBuilder.append("<span color='").append(MekHQ.getMHQOptions().getFontColorNegativeHexColor())
                .append("'>Contract objective located</span><br/>");
        }

        if (backingScenario != null) {
            stateBuilder.append("<b>Scenario:</b> ")
                .append(backingScenario.getName())
                .append("<br/>");

            if (backingScenario.getTemplate() != null) {
                stateBuilder.append("<i>").append(backingScenario.getTemplate().shortBriefing).append("</i>")
                    .append("<br/>");
            }

            if (isRequiredScenario()) {
                stateBuilder.append("<span color='").append(MekHQ.getMHQOptions().getFontColorNegativeHexColor())
                    .append("'>-1 VP if lost/ignored; +1 VP if won</span><br/>");
            }

            stateBuilder.append("<b>Status:</b> ")
                .append(currentState.getScenarioStateName())
                .append("<br/>");


                stateBuilder.append("<b>Terrain:</b> ")
                    .append(backingScenario.getMap())
                    .append("<br/>");

            if (deploymentDate != null) {
                stateBuilder.append("<b>Deployment Date:</b> ")
                    .append(deploymentDate)
                    .append("<br/>");
            }

            if (actionDate != null) {
                stateBuilder.append("<b>Battle Date:</b> ")
                    .append(actionDate)
                    .append("<br/>");
            }

            if (returnDate != null) {
                stateBuilder.append("<b>Return Date:</b> ")
                    .append(returnDate)
                    .append("<br/>");
            }

            if (campaign != null) {
                stateBuilder.append(String.format("<b>Hostile BV:</b> %d<br>",
                    backingScenario.getTeamTotalBattleValue(campaign, false)));
                stateBuilder.append(String.format("<b>Allied BV:</b> %d",
                    backingScenario.getTeamTotalBattleValue(campaign, true)));
            }
        }

        stateBuilder.append("</html>");
        return stateBuilder.toString();
    }

    public void updateMinefieldCount(int minefieldType, int number) {
        backingScenario.setNumPlayerMinefields(minefieldType, number);
    }

    public String getName() {
        return backingScenario.getName();
    }

    public int getRequiredPlayerLances() {
        return requiredPlayerLances;
    }


    public void setRequiredPlayerLances(int requiredPlayerLances) {
        this.requiredPlayerLances = requiredPlayerLances;
    }

    public void incrementRequiredPlayerLances() {
        requiredPlayerLances++;
    }

    public boolean isRequiredScenario() {
        return requiredScenario;
    }

    public void setRequiredScenario(boolean requiredScenario) {
        this.requiredScenario = requiredScenario;
    }

    @XmlTransient
    public AtBDynamicScenario getBackingScenario() {
        return backingScenario;
    }

    @XmlJavaTypeAdapter(DateAdapter.class)
    public LocalDate getDeploymentDate() {
        return deploymentDate;
    }

    public void setDeploymentDate(LocalDate deploymentDate) {
        this.deploymentDate = deploymentDate;
    }

    @XmlJavaTypeAdapter(DateAdapter.class)
    public LocalDate getActionDate() {
        return actionDate;
    }

    public void setActionDate(LocalDate actionDate) {
        this.actionDate = actionDate;
        backingScenario.setDate(actionDate);
    }

    @XmlJavaTypeAdapter(DateAdapter.class)
    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public StratconCoords getCoords() {
        return coords;
    }

    public void setCoords(StratconCoords coords) {
        this.coords = coords;
    }

    public boolean isStrategicObjective() {
        return isStrategicObjective;
    }

    public void setStrategicObjective(boolean value) {
        isStrategicObjective = value;
    }

    public ScenarioTemplate getScenarioTemplate() {
        return backingScenario.getTemplate();
    }

    public int getBackingScenarioID() {
        return backingScenarioID;
    }

    public void setBackingScenario(AtBDynamicScenario backingScenario) {
        this.backingScenario = backingScenario;

        setBackingScenarioID(backingScenario.getId());
    }

    public void setBackingScenarioID(int backingScenarioID) {
        this.backingScenarioID = backingScenarioID;
    }

    public int getNumDefensivePoints() {
        return numDefensivePoints;
    }

    public void setNumDefensivePoints(int numDefensivePoints) {
        this.numDefensivePoints = numDefensivePoints;
    }

    public void useDefensivePoint() {
        numDefensivePoints--;
    }

    public Set<Integer> getFailedReinforcements() {
        return failedReinforcements;
    }

    public void setFailedReinforcements(Set<Integer> failedReinforcements) {
        this.failedReinforcements = failedReinforcements;
    }

    public void addFailedReinforcements(int forceID) {
        failedReinforcements.add(forceID);
    }

    public boolean ignoreForceAutoAssignment() {
        return ignoreForceAutoAssignment;
    }

    public void setIgnoreForceAutoAssignment(boolean ignoreForceAutoAssignment) {
        this.ignoreForceAutoAssignment = ignoreForceAutoAssignment;
    }

    public int getLeadershipPointsUsed() {
        return leadershipPointsUsed;
    }

    public void setAvailableLeadershipBudget(int leadershipPointsUsed) {
        this.leadershipPointsUsed = leadershipPointsUsed;
    }
}
