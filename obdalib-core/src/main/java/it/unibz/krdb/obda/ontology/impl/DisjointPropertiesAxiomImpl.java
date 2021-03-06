package it.unibz.krdb.obda.ontology.impl;

/*
 * #%L
 * ontop-obdalib-core
 * %%
 * Copyright (C) 2009 - 2014 Free University of Bozen-Bolzano
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.Collections;
import java.util.Set;

import it.unibz.krdb.obda.ontology.DisjointPropertiesAxiom;
import it.unibz.krdb.obda.ontology.PropertyExpression;

public class DisjointPropertiesAxiomImpl implements DisjointPropertiesAxiom {

	private static final long serialVersionUID = 4456694617300452114L;
	
	private final Set<PropertyExpression> props;
	
	DisjointPropertiesAxiomImpl(Set<PropertyExpression> props) {
		if (props.size() < 2)
			throw new IllegalArgumentException("At least 2 properties are expeccted in DisjointClassAxiom");
		
		this.props = props;
	}
	
	@Override
	public Set<PropertyExpression> getProperties() {
		return Collections.unmodifiableSet(props);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof DisjointPropertiesAxiomImpl) {
			DisjointPropertiesAxiomImpl other = (DisjointPropertiesAxiomImpl)obj;
			return props.equals(other.props);
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return props.hashCode();
	}
	
	@Override
	public String toString() {
		return "disjoint(" + props + ")";
	}

}
