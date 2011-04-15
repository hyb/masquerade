package masquerade.sim.model.impl.step;

import masquerade.sim.model.SimulationContext;

/**
 * Sets the response content to a string
 */
public class SetContentStep extends AbstractSubstitutingStep {

	private String content = ""; 

	public SetContentStep(String name) {
		super(name);
	}
	
	/**
	 * @return the content
	 */
	public String getContent() {
		return content;
	}

	/**
	 * @param content the content to set
	 */
	public void setContent(String content) {
		this.content = content;
	}

	/**
	 * Substitutes variables and sets the content as string
	 */
	@Override
	public void execute(SimulationContext context) throws Exception {
		String content = substituteVariables(this.content, context);
		context.setContent(content);
	}
}