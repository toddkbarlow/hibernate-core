/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2011, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.metamodel.source.annotations.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.DiscriminatorType;
import javax.persistence.FetchType;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;

import org.hibernate.AnnotationException;
import org.hibernate.AssertionFailure;
import org.hibernate.annotations.GenerationTime;
import org.hibernate.mapping.PropertyGeneration;
import org.hibernate.metamodel.source.annotations.HibernateDotNames;
import org.hibernate.metamodel.source.annotations.JPADotNames;
import org.hibernate.metamodel.source.annotations.util.JandexHelper;

/**
 * Represent a mapped attribute (explicitly or implicitly mapped). Also used for synthetic attributes like a
 * discriminator column.
 *
 * @author Hardy Ferentschik
 */
public class MappedAttribute implements Comparable<MappedAttribute> {
	/**
	 * Annotations defined on the attribute, keyed against the annotation dot name.
	 */
	private final Map<DotName, List<AnnotationInstance>> annotations;

	/**
	 * The property name.
	 */
	private final String name;

	/**
	 * The property type as string.
	 */
	private final String type;

	/**
	 * Optional type parameters for custom types.
	 */
	private final Map<String, String> typeParameters;

	/**
	 * Is this property an id property (or part thereof).
	 */
	private final boolean isId;

	/**
	 * Is this a versioned property (annotated w/ {@code @Version}.
	 */
	private final boolean isVersioned;

	/**
	 * Is this property a discriminator property.
	 */
	private final boolean isDiscriminator;

	/**
	 * Whether a change of the property's value triggers a version increment of the entity (in case of optimistic
	 * locking).
	 */
	private final boolean isOptimisticLockable;

	/**
	 * Is this property lazy loaded (see {@link javax.persistence.Basic}).
	 */
	private boolean isLazy = false;

	/**
	 * Is this property optional  (see {@link javax.persistence.Basic}).
	 */
	private boolean isOptional = true;

	private PropertyGeneration propertyGeneration;
	private boolean isInsertable = true;
	private boolean isUpdatable = true;

	/**
	 * Defines the column values (relational values) for this property.
	 */
	private final ColumnValues columnValues;

	static MappedAttribute createMappedAttribute(String name, String type, Map<DotName, List<AnnotationInstance>> annotations) {
		return new MappedAttribute( name, type, annotations, false );
	}

	static MappedAttribute createDiscriminatorAttribute(Map<DotName, List<AnnotationInstance>> annotations) {
		AnnotationInstance discriminatorOptionsAnnotation = JandexHelper.getSingleAnnotation(
				annotations, JPADotNames.DISCRIMINATOR_COLUMN
		);
		String name = DiscriminatorColumnValues.DEFAULT_DISCRIMINATOR_COLUMN_NAME;
		String type = String.class.toString(); // string is the discriminator default
		if ( discriminatorOptionsAnnotation != null ) {
			name = discriminatorOptionsAnnotation.value( "name" ).asString();

			DiscriminatorType discriminatorType = Enum.valueOf(
					DiscriminatorType.class, discriminatorOptionsAnnotation.value( "discriminatorType" ).asEnum()
			);
			switch ( discriminatorType ) {
				case STRING: {
					type = String.class.toString();
					break;
				}
				case CHAR: {
					type = Character.class.toString();
					break;
				}
				case INTEGER: {
					type = Integer.class.toString();
					break;
				}
				default: {
					throw new AnnotationException( "Unsupported discriminator type: " + discriminatorType );
				}
			}
		}
		return new MappedAttribute( name, type, annotations, true );
	}

	private MappedAttribute(String name, String type, Map<DotName, List<AnnotationInstance>> annotations, boolean isDiscriminator) {
		this.name = name;
		this.annotations = annotations;
		this.isDiscriminator = isDiscriminator;

		this.typeParameters = new HashMap<String, String>();
		this.type = determineType( type, typeParameters );

		AnnotationInstance idAnnotation = JandexHelper.getSingleAnnotation( annotations, JPADotNames.ID );
		isId = idAnnotation != null;

		AnnotationInstance versionAnnotation = JandexHelper.getSingleAnnotation( annotations, JPADotNames.VERSION );
		isVersioned = versionAnnotation != null;

		if ( isDiscriminator ) {
			columnValues = new DiscriminatorColumnValues( annotations );
		}
		else {
			AnnotationInstance columnAnnotation = JandexHelper.getSingleAnnotation( annotations, JPADotNames.COLUMN );
			columnValues = new ColumnValues( columnAnnotation );
		}

		if ( isId ) {
			// an id must be unique and cannot be nullable
			columnValues.setUnique( true );
			columnValues.setNullable( false );
		}

		this.isOptimisticLockable = checkOptimisticLockAnnotation();

		checkBasicAnnotation();
		checkGeneratedAnnotation();
	}

	public final String getName() {
		return name;
	}

	public final String getType() {
		return type;
	}

	public final ColumnValues getColumnValues() {
		return columnValues;
	}

	public Map<String, String> getTypeParameters() {
		return typeParameters;
	}

	public boolean isId() {
		return isId;
	}

	public boolean isVersioned() {
		return isVersioned;
	}

	public boolean isDiscriminator() {
		return isDiscriminator;
	}

	public boolean isLazy() {
		return isLazy;
	}

	public boolean isOptional() {
		return isOptional;
	}

	public boolean isInsertable() {
		return isInsertable;
	}

	public boolean isUpdatable() {
		return isUpdatable;
	}

	public PropertyGeneration getPropertyGeneration() {
		return propertyGeneration;
	}

	public boolean isOptimisticLockable() {
		return isOptimisticLockable;
	}

	/**
	 * Returns the annotation with the specified name or {@code null}
	 *
	 * @param annotationDotName The annotation to retrieve/check
	 *
	 * @return Returns the annotation with the specified name or {@code null}. Note, since these are the
	 *         annotations defined on a single attribute there can never be more than one.
	 */
	public final AnnotationInstance getIfExists(DotName annotationDotName) {
		if ( annotations.containsKey( annotationDotName ) ) {
			List<AnnotationInstance> instanceList = annotations.get( annotationDotName );
			if ( instanceList.size() > 1 ) {
				throw new AssertionFailure( "There cannot be more than one @" + annotationDotName.toString() + " annotation per mapped attribute" );
			}
			return instanceList.get( 0 );
		}
		else {
			return null;
		}
	}

	@Override
	public int compareTo(MappedAttribute mappedProperty) {
		return name.compareTo( mappedProperty.getName() );
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append( "MappedAttribute" );
		sb.append( "{name='" ).append( name ).append( '\'' );
		sb.append( ", type='" ).append( type ).append( '\'' );
		sb.append( ", isId=" ).append( isId );
		sb.append( ", isVersioned=" ).append( isVersioned );
		sb.append( ", isDiscriminator=" ).append( isDiscriminator );
		sb.append( '}' );
		return sb.toString();
	}

	/**
	 * We need to check whether the is an explicit type specified via {@link org.hibernate.annotations.Type}.
	 *
	 * @param type the type specified via the constructor
	 * @param typeParameters map for type parameters in case there are any
	 *
	 * @return the final type for this mapped attribute
	 */
	private String determineType(String type, Map<String, String> typeParameters) {
		AnnotationInstance typeAnnotation = getIfExists( HibernateDotNames.TYPE );
		if ( typeAnnotation == null ) {
			// return discovered type
			return type;
		}

		AnnotationValue parameterAnnotationValue = typeAnnotation.value( "parameters" );
		if ( parameterAnnotationValue != null ) {
			AnnotationInstance[] parameterAnnotations = parameterAnnotationValue.asNestedArray();
			for ( AnnotationInstance parameterAnnotationInstance : parameterAnnotations ) {
				typeParameters.put(
						parameterAnnotationInstance.value( "name" ).asString(),
						parameterAnnotationInstance.value( "value" ).asString()
				);
			}
		}

		return typeAnnotation.value( "type" ).asString();
	}

	private boolean checkOptimisticLockAnnotation() {
		boolean triggersVersionIncrement = true;
		AnnotationInstance optimisticLockAnnotation = getIfExists( HibernateDotNames.OPTIMISTIC_LOCK );
		if ( optimisticLockAnnotation != null ) {
			boolean exclude = optimisticLockAnnotation.value( "excluded" ).asBoolean();
			triggersVersionIncrement = !exclude;
		}
		return triggersVersionIncrement;
	}

	private void checkBasicAnnotation() {
		AnnotationInstance basicAnnotation = getIfExists( JPADotNames.BASIC );
		if ( basicAnnotation != null ) {
			FetchType fetchType = FetchType.LAZY;
			AnnotationValue fetchValue = basicAnnotation.value( "fetch" );
			if ( fetchValue != null ) {
				fetchType = Enum.valueOf( FetchType.class, fetchValue.asEnum() );
			}
			this.isLazy = fetchType == FetchType.LAZY;

			AnnotationValue optionalValue = basicAnnotation.value( "optional" );
			if ( optionalValue != null ) {
				this.isOptional = optionalValue.asBoolean();
			}
		}
	}

	// TODO - there is more todo for updatable and insertable. Checking the @Generated annotation is only one part (HF)
	private void checkGeneratedAnnotation() {
		AnnotationInstance generatedAnnotation = getIfExists( HibernateDotNames.GENERATED );
		if ( generatedAnnotation != null ) {
			this.isInsertable = false;

			AnnotationValue generationTimeValue = generatedAnnotation.value();
			if ( generationTimeValue != null ) {
				GenerationTime genTime = Enum.valueOf( GenerationTime.class, generationTimeValue.asEnum() );
				if ( GenerationTime.ALWAYS.equals( genTime ) ) {
					this.isUpdatable = false;
					this.propertyGeneration = PropertyGeneration.parse( genTime.toString().toLowerCase() );
				}
			}
		}
	}
}


