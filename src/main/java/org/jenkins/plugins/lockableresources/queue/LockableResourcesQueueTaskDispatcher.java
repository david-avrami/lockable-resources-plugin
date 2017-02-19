/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * Copyright (c) 2013, 6WIND S.A. All rights reserved.                 *
 *                                                                     *
 * This file is part of the Jenkins Lockable Resources Plugin and is   *
 * published under the MIT license.                                    *
 *                                                                     *
 * See the "LICENSE.txt" file for more information.                    *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
package org.jenkins.plugins.lockableresources.queue;

import hudson.Extension;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.model.queue.CauseOfBlockage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.jenkins.plugins.lockableresources.LockableResource;
import org.jenkins.plugins.lockableresources.LockableResourcesManager;

@Extension
public class LockableResourcesQueueTaskDispatcher extends QueueTaskDispatcher {

	static final Logger LOGGER = Logger
			.getLogger(LockableResourcesQueueTaskDispatcher.class.getName());

	@Override
	public CauseOfBlockage canRun(Queue.Item item) {
		// Skip locking for multiple configuration projects,
		// only the child jobs will actually lock resources.
		if (item.task instanceof MatrixProject)
			return null;

		Job<?, ?> project = Utils.getProject(item);
		if (project == null)
			return null;

		LockableResourcesStruct resources = Utils.requiredResources(project);
		if (resources == null ||
			(resources.required.isEmpty() && resources.label.isEmpty())) {
			return null;
		}
		
//		int resourceNumber = 0; 
//		try {
//			resourceNumber = Integer.parseInt(resources.requiredNumber);
//		} catch (NumberFormatException e) {
//			LOGGER.finest(project.getName() + " Didn't get number of resources with message:" + e.getMessage());
//			LOGGER.finest(project.getName() + " Setting number of resources to default value");
//			resourceNumber = 0;
//		}
		
		int sumOfNeededRes = 0; 
		if (resources.requiredNumber != null && !resources.requiredNumber.isEmpty()) {
			ArrayList<String> numbersForLabels = new ArrayList<String>(Arrays.asList(resources.requiredNumber.split("\\s+")));
			for (String reqNum : numbersForLabels) {
				sumOfNeededRes += Integer.parseInt(reqNum);
			}
		}

		LOGGER.finest(project.getName() +
			" trying to get resources with these details: " + resources);

		if (sumOfNeededRes > 0 || !resources.label.isEmpty()) {
			Map<String, Object> params = new HashMap<String, Object>();
			if (item.task instanceof MatrixConfiguration) {
			    MatrixConfiguration matrix = (MatrixConfiguration) item.task;
			    params.putAll(matrix.getCombination());
			}

			List<LockableResource> selected = LockableResourcesManager.get().queue(
					resources,
					item.getId(),
					project.getFullName(),
					sumOfNeededRes,
					params,
					LOGGER);

			if (selected != null) {
				System.out.println("Reserved:" + selected);
				LOGGER.finest(project.getName() + " reserved resources " + selected);
				return null;
			} else {
				LOGGER.finest(project.getName() + " waiting for resources");
				return new BecauseResourcesLocked(resources);
			}

		} else {
			if (LockableResourcesManager.get().queue(resources.required, item.getId())) {
				LOGGER.finest(project.getName() + " reserved resources " + resources.required);
				return null;
			} else {
				LOGGER.finest(project.getName() + " waiting for resources "
					+ resources.required);
				return new BecauseResourcesLocked(resources);
			}
		}
	}

	public static class BecauseResourcesLocked extends CauseOfBlockage {

		private final LockableResourcesStruct rscStruct;

		public BecauseResourcesLocked(LockableResourcesStruct r) {
			this.rscStruct = r;
		}

		@Override
		public String getShortDescription() {
			if (this.rscStruct.label.isEmpty())
				return "Waiting for resources " + rscStruct.required.toString();
			else
				return "Waiting for resources with label " + rscStruct.label;
		}
	}

}
