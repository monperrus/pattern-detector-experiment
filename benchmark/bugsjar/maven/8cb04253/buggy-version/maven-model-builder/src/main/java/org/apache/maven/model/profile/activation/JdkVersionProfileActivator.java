package org.apache.maven.model.profile.activation;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.model.Activation;
import org.apache.maven.model.Profile;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.building.ModelProblem.Severity;
import org.apache.maven.model.profile.ProfileActivationContext;
import org.codehaus.plexus.component.annotations.Component;

/**
 * Determines profile activation based on the version of the current Java runtime.
 * 
 * @author Benjamin Bentmann
 */
@Component( role = ProfileActivator.class, hint = "jdk-version" )
public class JdkVersionProfileActivator
    implements ProfileActivator
{

    public boolean isActive( Profile profile, ProfileActivationContext context, ModelProblemCollector problems )
    {
        boolean active = false;

        Activation activation = profile.getActivation();

        if ( activation != null )
        {
            String jdk = activation.getJdk();

            if ( jdk != null )
            {
                String version = context.getSystemProperties().getProperty( "java.version", "" );

                if ( version.length() <= 0 )
                {
                    problems.add( Severity.ERROR, "Failed to determine Java version for profile " + profile.getId(),
                                  null );
                    return false;
                }

                if ( jdk.startsWith( "!" ) )
                {
                    active = !version.startsWith( jdk.substring( 1 ) );
                }
                else if ( isRange( jdk ) )
                {
                    active = isInRange( version, getRange( jdk ) );
                }
                else
                {
                    active = version.startsWith( jdk );
                }
            }
        }

        return active;
    }

    private static boolean isInRange( String value, List<RangeValue> range )
    {
        int leftRelation = getRelationOrder( value, range.get( 0 ), true );

        if ( leftRelation == 0 )
        {
            return true;
        }

        if ( leftRelation < 0 )
        {
            return false;
        }

        return getRelationOrder( value, range.get( 1 ), false ) <= 0;
    }

    private static int getRelationOrder( String value, RangeValue rangeValue, boolean isLeft )
    {
        if ( rangeValue.value.length() <= 0 )
        {
            return isLeft ? 1 : -1;
        }

        List<String> valueTokens = new ArrayList<String>( Arrays.asList( value.split( "\\." ) ) );
        List<String> rangeValueTokens = new ArrayList<String>( Arrays.asList( rangeValue.value.split( "\\." ) ) );

        int max = Math.max( valueTokens.size(), rangeValueTokens.size() );
        addZeroTokens( valueTokens, max );
        addZeroTokens( rangeValueTokens, max );

        if ( value.equals( rangeValue.getValue() ) )
        {
            if ( !rangeValue.isClosed() )
            {
                return isLeft ? -1 : 1;
            }
            return 0;
        }

        for ( int i = 0; i < valueTokens.size(); i++ )
        {
            int x = Integer.parseInt( valueTokens.get( i ) );
            int y = Integer.parseInt( rangeValueTokens.get( i ) );
            if ( x < y )
            {
                return -1;
            }
            else if ( x > y )
            {
                return 1;
            }
        }
        if ( !rangeValue.isClosed() )
        {
            return isLeft ? -1 : 1;
        }
        return 0;
    }

    private static void addZeroTokens( List<String> tokens, int max )
    {
        if ( tokens.size() < max )
        {
            for ( int i = 0; i < ( max - tokens.size() ); i++ )
            {
                tokens.add( "0" );
            }
        }
    }

    private static boolean isRange( String value )
    {
        return value.startsWith( "[" ) || value.startsWith( "(" );
    }

    private static List<RangeValue> getRange( String range )
    {
        List<RangeValue> ranges = new ArrayList<RangeValue>();

        for ( String token : range.split( "," ) )
        {
            if ( token.startsWith( "[" ) )
            {
                ranges.add( new RangeValue( token.replace( "[", "" ), true ) );
            }
            else if ( token.startsWith( "(" ) )
            {
                ranges.add( new RangeValue( token.replace( "(", "" ), false ) );
            }
            else if ( token.endsWith( "]" ) )
            {
                ranges.add( new RangeValue( token.replace( "]", "" ), true ) );
            }
            else if ( token.endsWith( ")" ) )
            {
                ranges.add( new RangeValue( token.replace( ")", "" ), false ) );
            }
            else if ( token.length() <= 0 )
            {
                ranges.add( new RangeValue( "", false ) );
            }
        }
        if ( ranges.size() < 2 )
        {
            ranges.add( new RangeValue( "99999999", false ) );
        }
        return ranges;
    }

    private static class RangeValue
    {
        private String value;

        private boolean isClosed;

        RangeValue( String value, boolean isClosed )
        {
            this.value = value.trim();
            this.isClosed = isClosed;
        }

        public String getValue()
        {
            return value;
        }

        public boolean isClosed()
        {
            return isClosed;
        }

        public String toString()
        {
            return value;
        }
    }

}
