/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

package org.hibernate.search.query.dsl.impl;

import org.apache.lucene.search.Filter;

import org.hibernate.search.query.dsl.TermMatchingContext;
import org.hibernate.search.query.dsl.WildcardContext;

/**
 * @author Emmanuel Bernard
 */
class ConnectedWildcardContext implements WildcardContext {
	private final QueryBuildingContext queryContext;
	private final QueryCustomizer queryCustomizer;
	private final TermQueryContext termContext;

	public ConnectedWildcardContext(QueryCustomizer queryCustomizer, QueryBuildingContext queryContext) {
		this.queryContext = queryContext;
		this.queryCustomizer = queryCustomizer;
		this.termContext = new TermQueryContext( TermQueryContext.Approximation.WILDCARD );
	}

	@Override
	public TermMatchingContext onField(String field) {
		return new ConnectedTermMatchingContext( termContext, field, queryCustomizer, queryContext);
	}

	@Override
	public WildcardContext boostedTo(float boost) {
		queryCustomizer.boostedTo( boost );
		return this;
	}

	@Override
	public WildcardContext withConstantScore() {
		queryCustomizer.withConstantScore();
		return this;
	}

	@Override
	public WildcardContext filteredBy(Filter filter) {
		queryCustomizer.filteredBy( filter );
		return this;
	}
}
