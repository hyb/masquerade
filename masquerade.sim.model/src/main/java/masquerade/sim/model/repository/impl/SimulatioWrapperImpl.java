package masquerade.sim.model.repository.impl;

import masquerade.sim.model.Simulation;
import masquerade.sim.model.repository.SimulationWrapper;

public class SimulatioWrapperImpl implements SimulationWrapper {

	private final Simulation simulation;
	private final boolean isPersistent;

	public SimulatioWrapperImpl(Simulation simulation, boolean isPersistent) {
		this.simulation = simulation;
		this.isPersistent = isPersistent;
	}

	@Override
	public Simulation getSimulation() {
		return simulation;
	}

	@Override
	public boolean isPersistent() {
		return isPersistent;
	}
}
