/*
 * Copyright (c) 2022-2024 - The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MekHQ. If not, see <http://www.gnu.org/licenses/>.
 */
package mekhq.campaign.personnel.divorce;

import megamek.common.Compute;
import mekhq.campaign.CampaignOptions;
import mekhq.campaign.personnel.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(value = MockitoExtension.class)
public class PercentageRandomDivorceTest {
    @Mock
    private CampaignOptions mockOptions;

    @Mock
    private Person mockPerson;

    @BeforeEach
    public void beforeEach() {
        when(mockOptions.isUseClanPersonnelDivorce()).thenReturn(false);
        when(mockOptions.isUsePrisonerDivorce()).thenReturn(false);
        when(mockOptions.isUseRandomOppositeSexDivorce()).thenReturn(false);
        when(mockOptions.isUseRandomSameSexDivorce()).thenReturn(false);
        when(mockOptions.isUseRandomClanPersonnelDivorce()).thenReturn(false);
        when(mockOptions.isUseRandomPrisonerDivorce()).thenReturn(false);
        when(mockOptions.getRandomDivorceDiceSize()).thenReturn(5);
    }

    @Test
    public void testRandomDivorce() {
        final RandomDivorce randomDivorce = new RandomDivorce(mockOptions);

        int diceSize = 5;

        try(MockedStatic<Compute> compute = Mockito.mockStatic(Compute.class)) {
            compute.when(() -> Compute.randomInt(diceSize)).thenReturn(0);
            assertTrue(randomDivorce.randomDivorce());
            compute.when(() -> Compute.randomInt(diceSize)).thenReturn(1);
            assertFalse(randomDivorce.randomDivorce());
        }
    }
}
