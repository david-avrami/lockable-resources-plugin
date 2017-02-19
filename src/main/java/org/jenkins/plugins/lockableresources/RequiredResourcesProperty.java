/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * Copyright (c) 2013, 6WIND S.A. All rights reserved.                 *
 *                                                                     *
 * This file is part of the Jenkins Lockable Resources Plugin and is   *
 * published under the MIT license.                                    *
 *                                                                     *
 * See the "LICENSE.txt" file for more information.                    *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
package org.jenkins.plugins.lockableresources;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Job;
import hudson.util.FormValidation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class RequiredResourcesProperty extends JobProperty<Job<?, ?>> {

	private final String resourceNames;
	private final String resourceNamesVar;
	private final String resourceNumber;
	private final String labelName;

	@DataBoundConstructor
	public RequiredResourcesProperty(String resourceNames,
			String resourceNamesVar, String resourceNumber,
			String labelName) {
		super();
		this.resourceNames = resourceNames;
		this.resourceNamesVar = resourceNamesVar;
		this.resourceNumber = resourceNumber;
		this.labelName = labelName;
	}

	public String[] getResources() {
		String names = Util.fixEmptyAndTrim(resourceNames);
		if (names != null)
			return names.split("\\s+");
		else
			return new String[0];
	}

	public String getResourceNames() {
		return resourceNames;
	}

	public String getResourceNamesVar() {
		return resourceNamesVar;
	}

	public String getResourceNumber() {
		return resourceNumber;
	}

	public String getLabelName() {
		return labelName;
	}

	@Extension
	public static class DescriptorImpl extends JobPropertyDescriptor {

		@Override
		public String getDisplayName() {
			return "Required Lockable Resources";
		}

		@Override
		public boolean isApplicable(Class<? extends Job> jobType) {
			return AbstractProject.class.isAssignableFrom(jobType);
		}

		@Override
		public RequiredResourcesProperty newInstance(StaplerRequest req,
				JSONObject formData) throws FormException {

			if (formData.isNullObject())
				return null;

			JSONObject json = formData
					.getJSONObject("required-lockable-resources");
			if (json.isNullObject())
				return null;

			String resourceNames = Util.fixEmptyAndTrim(json
					.getString("resourceNames"));

			String resourceNamesVar = Util.fixEmptyAndTrim(json
					.getString("resourceNamesVar"));

			String resourceNumber = Util.fixEmptyAndTrim(json
					.getString("resourceNumber"));

			String labelName = Util.fixEmptyAndTrim(json
					.getString("labelName"));

			if (resourceNames == null && labelName == null)
				return null;

			return new RequiredResourcesProperty(resourceNames,
					resourceNamesVar, resourceNumber, labelName);
		}

		public FormValidation doCheckResourceNames(@QueryParameter String value) {
			String names = Util.fixEmptyAndTrim(value);
			if (names == null) {
				return FormValidation.ok();
			} else {
				List<String> wrongNames = new ArrayList<String>();
				for (String name : names.split("\\s+")) {
					boolean found = false;
					for (LockableResource r : LockableResourcesManager.get()
							.getResources()) {
						if (r.getName().equals(name)) {
							found = true;
							break;
						}
					}
					if (!found)
						wrongNames.add(name);
				}
				if (wrongNames.isEmpty()) {
					return FormValidation.ok();
				} else {
					return FormValidation
							.error("The following resources do not exist: "
									+ wrongNames);
				}
			}
		}

		public FormValidation doCheckLabelName(
				@QueryParameter String value,
				@QueryParameter String resourceNames) {
			String label = Util.fixEmptyAndTrim(value);
			String names = Util.fixEmptyAndTrim(resourceNames);
			if (label == null) {
				return FormValidation.ok();
			} else if (names != null) {
				return FormValidation.error(
						"Only label or resources can be defined, not both.");
			} else {
				List<String> asList = Arrays.asList(label.split("\\s+"));
				for (String labelFromList : asList) {
					if (!LockableResourcesManager.get().isValidLabel(labelFromList)) {
						return FormValidation.error(
								"The label does not exist: " + labelFromList);
					}
				}

				return FormValidation.ok();
			}
		}

		public FormValidation doCheckResourceNumber(@QueryParameter String value,
				@QueryParameter String resourceNames,
				@QueryParameter String labelName) {

			String number = Util.fixEmptyAndTrim(value);
			String names = Util.fixEmptyAndTrim(resourceNames);
			String labels = Util.fixEmptyAndTrim(labelName);


			if (names != null) {
				if (number != null && !number.isEmpty()) {
					return FormValidation.error("Remove number when using 'Resources'");
				}
				
				if (number == null || number.equals("") || number.trim().equals("0")) {
					return FormValidation.ok();
				}
				
				int numAsInt;
				try {
					numAsInt = Integer.parseInt(number);
				} catch(NumberFormatException e)  {
					return FormValidation.error("Could not parse the given value as integer.");
				}
				
				int numResources = names.split("\\s+").length;

				if (numResources < numAsInt) {
					return FormValidation.error(String.format(
							"Given amount %d is greater than amount of resources: %d.",
							numAsInt,
							numResources));
				}
			} else if (labels != null) {
				int numLabels = labels.split("\\s+").length;
				
				if (number == null || number.isEmpty()) {
					return FormValidation.error("Please fill the number values");
				}
				
				List<String> numbersAsList = Arrays.asList(number.split("\\s+"));
				if (numLabels != numbersAsList.size()) {
					return FormValidation.error(String.format(
							"Given amount of numbers %d is not equeal to the amount of labels: %d.",
							numbersAsList.size(),
							numLabels));
				}
				for (String num : numbersAsList) {
					if (Integer.parseInt(num) <= 0)
						return FormValidation.error("0 is not a valid resource number");
				}
				
				ArrayList<String> removeMe = new ArrayList<String>(Arrays.asList(labels.split("\\s+")));
				ArrayList<String> fromMe = new ArrayList<String>(Arrays.asList(labels.split("\\s+")));

				for (String o1 : removeMe) {
					fromMe.remove(o1);
					if (fromMe.remove(o1))
						return FormValidation.error("Use each lable only once");
				}
				
				ArrayList<String> resLabels = new ArrayList<String>(Arrays.asList(labels.split("\\s+")));
				ArrayList<String> arrOfNumbers = new ArrayList<String>(Arrays.asList(number.split("\\s+")));
				for (int i = 0; i < resLabels.size(); i++) {
					String l = resLabels.get(i);
					Integer num = Integer.parseInt(arrOfNumbers.get(i));
					int actualSizeOfRes = LockableResourcesManager.get().getResourcesWithLabel(new ArrayList<String>(Arrays.asList(l)), null).size();
					if (actualSizeOfRes < num)
						return FormValidation.error("There are only " + actualSizeOfRes + " resources with the label: " + l);
				}
			}

			return FormValidation.ok();
		}

		public AutoCompletionCandidates doAutoCompleteLabelName(
				@QueryParameter String value) {
			AutoCompletionCandidates c = new AutoCompletionCandidates();

			value = Util.fixEmptyAndTrim(value);

			for (String l : LockableResourcesManager.get().getAllLabels())
				if (value != null && l.startsWith(value))
					c.add(l);

			return c;
		}

		public static AutoCompletionCandidates doAutoCompleteResourceNames(
				@QueryParameter String value) {
			AutoCompletionCandidates c = new AutoCompletionCandidates();

			value = Util.fixEmptyAndTrim(value);

			if (value != null) {
				for (LockableResource r : LockableResourcesManager.get()
						.getResources()) {
					if (r.getName().startsWith(value))
						c.add(r.getName());
				}
			}

			return c;
		}
	}
}

