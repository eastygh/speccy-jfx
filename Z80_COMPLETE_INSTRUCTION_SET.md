# Complete Z80 Instruction Set Reference

## Overview

The Z80 processor has approximately 700+ instruction variations when including all prefixes, addressing modes, and undocumented opcodes. This document provides a comprehensive reference for implementing a complete Z80 CPU emulator.

## Instruction Format Structure

### Basic Opcode Categories
1. **Single-byte opcodes (0x00-0xFF)** - Base instruction set
2. **CB prefixed (0xCB xx)** - Bit operations, rotates, shifts
3. **DD prefixed (0xDD xx)** - IX register operations
4. **FD prefixed (0xFD xx)** - IY register operations
5. **ED prefixed (0xED xx)** - Extended instruction set
6. **DDCB/FDCB prefixed (0xDD 0xCB d xx / 0xFD 0xCB d xx)** - Indexed bit operations

### Register Encoding
- **r = 8-bit registers**: B(000), C(001), D(010), E(011), H(100), L(101), (HL)(110), A(111)
- **rr = 16-bit pairs**: BC(00), DE(01), HL(10), SP(11) or AF(11 for POP/PUSH)
- **cc = conditions**: NZ(00), Z(01), NC(10), C(11), PO(100), PE(101), P(110), M(111)

## Complete Instruction Set by Category

### 1. 8-Bit Load Instructions

#### LD r,r' (Load register to register)
| Opcode Pattern | Mnemonic | T-States | Flags | Notes |
|----------------|----------|----------|-------|-------|
| 01 ddd sss | LD r,r' | 4 | - | All combinations except (HL),(HL) |

**Implementation Pattern:**
```
Opcodes: 0x40-0x7F (except 0x76 which is HALT)
Destination: bits 5-3, Source: bits 2-0
0x40: LD B,B  0x41: LD B,C  ... 0x7F: LD A,A
```

#### LD r,n (Load immediate to register)
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0x06 | LD B,n | 7 | - |
| 0x0E | LD C,n | 7 | - |
| 0x16 | LD D,n | 7 | - |
| 0x1E | LD E,n | 7 | - |
| 0x26 | LD H,n | 7 | - |
| 0x2E | LD L,n | 7 | - |
| 0x36 | LD (HL),n | 10 | - |
| 0x3E | LD A,n | 7 | - |

#### LD r,(HL) / LD (HL),r (Memory access via HL)
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0x46 | LD B,(HL) | 7 | - |
| 0x4E | LD C,(HL) | 7 | - |
| 0x56 | LD D,(HL) | 7 | - |
| 0x5E | LD E,(HL) | 7 | - |
| 0x66 | LD H,(HL) | 7 | - |
| 0x6E | LD L,(HL) | 7 | - |
| 0x7E | LD A,(HL) | 7 | - |
| 0x70 | LD (HL),B | 7 | - |
| 0x71 | LD (HL),C | 7 | - |
| 0x72 | LD (HL),D | 7 | - |
| 0x73 | LD (HL),E | 7 | - |
| 0x74 | LD (HL),H | 7 | - |
| 0x75 | LD (HL),L | 7 | - |
| 0x77 | LD (HL),A | 7 | - |

#### Extended Memory Load Instructions
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0x0A | LD A,(BC) | 7 | - |
| 0x1A | LD A,(DE) | 7 | - |
| 0x3A | LD A,(nn) | 13 | - |
| 0x02 | LD (BC),A | 7 | - |
| 0x12 | LD (DE),A | 7 | - |
| 0x32 | LD (nn),A | 13 | - |

### 2. 16-Bit Load Instructions

#### LD rr,nn (Load immediate to register pair)
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0x01 | LD BC,nn | 10 | - |
| 0x11 | LD DE,nn | 10 | - |
| 0x21 | LD HL,nn | 10 | - |
| 0x31 | LD SP,nn | 10 | - |

#### Extended 16-bit Loads (ED prefix)
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0xED43 | LD (nn),BC | 20 | - |
| 0xED53 | LD (nn),DE | 20 | - |
| 0xED63 | LD (nn),HL | 20 | - |
| 0xED73 | LD (nn),SP | 20 | - |
| 0xED4B | LD BC,(nn) | 20 | - |
| 0xED5B | LD DE,(nn) | 20 | - |
| 0xED6B | LD HL,(nn) | 20 | - |
| 0xED7B | LD SP,(nn) | 20 | - |

#### Stack Operations
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0xC1 | POP BC | 10 | - |
| 0xD1 | POP DE | 10 | - |
| 0xE1 | POP HL | 10 | - |
| 0xF1 | POP AF | 10 | All flags from popped value |
| 0xC5 | PUSH BC | 11 | - |
| 0xD5 | PUSH DE | 11 | - |
| 0xE5 | PUSH HL | 11 | - |
| 0xF5 | PUSH AF | 11 | - |

### 3. 8-Bit Arithmetic Instructions

#### Basic Arithmetic (with A register)
| Opcode Pattern | Mnemonic | T-States | Flags |
|----------------|----------|----------|-------|
| 10000sss | ADD A,r | 4 | S Z H PV N C |
| 10001sss | ADC A,r | 4 | S Z H PV N C |
| 10010sss | SUB r | 4 | S Z H PV N C |
| 10011sss | SBC A,r | 4 | S Z H PV N C |
| 10100sss | AND r | 4 | S Z H PV N C |
| 10101sss | XOR r | 4 | S Z H PV N C |
| 10110sss | OR r | 4 | S Z H PV N C |
| 10111sss | CP r | 4 | S Z H PV N C |

**Specific Opcodes:**
```
ADD: 0x80-0x87, ADC: 0x88-0x8F, SUB: 0x90-0x97, SBC: 0x98-0x9F
AND: 0xA0-0xA7, XOR: 0xA8-0xAF, OR:  0xB0-0xB7, CP:  0xB8-0xBF
```

#### Immediate Arithmetic
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0xC6 | ADD A,n | 7 | S Z H PV N C |
| 0xCE | ADC A,n | 7 | S Z H PV N C |
| 0xD6 | SUB n | 7 | S Z H PV N C |
| 0xDE | SBC A,n | 7 | S Z H PV N C |
| 0xE6 | AND n | 7 | S Z H PV N C |
| 0xEE | XOR n | 7 | S Z H PV N C |
| 0xF6 | OR n | 7 | S Z H PV N C |
| 0xFE | CP n | 7 | S Z H PV N C |

#### Increment/Decrement 8-bit
| Opcode Pattern | Mnemonic | T-States | Flags |
|----------------|----------|----------|-------|
| 00 rrr 100 | INC r | 4 | S Z H PV N |
| 00 rrr 101 | DEC r | 4 | S Z H PV N |

**Specific Opcodes:**
```
INC: 0x04(B), 0x0C(C), 0x14(D), 0x1C(E), 0x24(H), 0x2C(L), 0x34((HL)), 0x3C(A)
DEC: 0x05(B), 0x0D(C), 0x15(D), 0x1D(E), 0x25(H), 0x2D(L), 0x35((HL)), 0x3D(A)
```

### 4. 16-Bit Arithmetic Instructions

#### ADD HL,rr
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0x09 | ADD HL,BC | 11 | H N C |
| 0x19 | ADD HL,DE | 11 | H N C |
| 0x29 | ADD HL,HL | 11 | H N C |
| 0x39 | ADD HL,SP | 11 | H N C |

#### Extended 16-bit Arithmetic (ED prefix)
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0xED4A | ADC HL,BC | 15 | S Z H PV N C |
| 0xED5A | ADC HL,DE | 15 | S Z H PV N C |
| 0xED6A | ADC HL,HL | 15 | S Z H PV N C |
| 0xED7A | ADC HL,SP | 15 | S Z H PV N C |
| 0xED42 | SBC HL,BC | 15 | S Z H PV N C |
| 0xED52 | SBC HL,DE | 15 | S Z H PV N C |
| 0xED62 | SBC HL,HL | 15 | S Z H PV N C |
| 0xED72 | SBC HL,SP | 15 | S Z H PV N C |

#### Increment/Decrement 16-bit
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0x03 | INC BC | 6 | - |
| 0x13 | INC DE | 6 | - |
| 0x23 | INC HL | 6 | - |
| 0x33 | INC SP | 6 | - |
| 0x0B | DEC BC | 6 | - |
| 0x1B | DEC DE | 6 | - |
| 0x2B | DEC HL | 6 | - |
| 0x3B | DEC SP | 6 | - |

### 5. Jump Instructions

#### Unconditional Jumps
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0xC3 | JP nn | 10 | - |
| 0xE9 | JP (HL) | 4 | - |
| 0x18 | JR e | 12 | - |

#### Conditional Jumps
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0xC2 | JP NZ,nn | 10 | - |
| 0xCA | JP Z,nn | 10 | - |
| 0xD2 | JP NC,nn | 10 | - |
| 0xDA | JP C,nn | 10 | - |
| 0xE2 | JP PO,nn | 10 | - |
| 0xEA | JP PE,nn | 10 | - |
| 0xF2 | JP P,nn | 10 | - |
| 0xFA | JP M,nn | 10 | - |

#### Conditional Relative Jumps
| Opcode | Mnemonic | T-States (taken/not) | Flags |
|--------|----------|---------------------|-------|
| 0x20 | JR NZ,e | 12/7 | - |
| 0x28 | JR Z,e | 12/7 | - |
| 0x30 | JR NC,e | 12/7 | - |
| 0x38 | JR C,e | 12/7 | - |

### 6. Call and Return Instructions

#### Call Instructions
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0xCD | CALL nn | 17 | - |
| 0xC4 | CALL NZ,nn | 17/10 | - |
| 0xCC | CALL Z,nn | 17/10 | - |
| 0xD4 | CALL NC,nn | 17/10 | - |
| 0xDC | CALL C,nn | 17/10 | - |
| 0xE4 | CALL PO,nn | 17/10 | - |
| 0xEC | CALL PE,nn | 17/10 | - |
| 0xF4 | CALL P,nn | 17/10 | - |
| 0xFC | CALL M,nn | 17/10 | - |

#### Return Instructions
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0xC9 | RET | 10 | - |
| 0xC0 | RET NZ | 11/5 | - |
| 0xC8 | RET Z | 11/5 | - |
| 0xD0 | RET NC | 11/5 | - |
| 0xD8 | RET C | 11/5 | - |
| 0xE0 | RET PO | 11/5 | - |
| 0xE8 | RET PE | 11/5 | - |
| 0xF0 | RET P | 11/5 | - |
| 0xF8 | RET M | 11/5 | - |

#### Restart Instructions
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0xC7 | RST 0x00 | 11 | - |
| 0xCF | RST 0x08 | 11 | - |
| 0xD7 | RST 0x10 | 11 | - |
| 0xDF | RST 0x18 | 11 | - |
| 0xE7 | RST 0x20 | 11 | - |
| 0xEF | RST 0x28 | 11 | - |
| 0xF7 | RST 0x30 | 11 | - |
| 0xFF | RST 0x38 | 11 | - |

### 7. CB Prefix Instructions (Bit Operations)

#### Rotate and Shift Instructions (CB 00-3F)
| Pattern | Mnemonic | Operation |
|---------|----------|-----------|
| CB 00xxx | RLC r | Rotate left circular |
| CB 01xxx | RRC r | Rotate right circular |
| CB 10xxx | RL r | Rotate left through carry |
| CB 11xxx | RR r | Rotate right through carry |
| CB 100xxx | SLA r | Shift left arithmetic |
| CB 101xxx | SRA r | Shift right arithmetic |
| CB 110xxx | SLL r | Shift left logical (undocumented) |
| CB 111xxx | SRL r | Shift right logical |

#### Bit Test Instructions (CB 40-7F)
| Pattern | Mnemonic | T-States |
|---------|----------|----------|
| CB 01bbbrrr | BIT b,r | 8 |

Where b = bit number (0-7), r = register

#### Bit Set Instructions (CB 80-BF)
| Pattern | Mnemonic | T-States |
|---------|----------|----------|
| CB 10bbbrrr | RES b,r | 8 |

#### Bit Reset Instructions (CB C0-FF)
| Pattern | Mnemonic | T-States |
|---------|----------|----------|
| CB 11bbbrrr | SET b,r | 8 |

### 8. ED Prefix Instructions (Extended Set)

#### I/O Instructions
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0xED40 | IN B,(C) | 12 | S Z H PV N |
| 0xED48 | IN C,(C) | 12 | S Z H PV N |
| 0xED50 | IN D,(C) | 12 | S Z H PV N |
| 0xED58 | IN E,(C) | 12 | S Z H PV N |
| 0xED60 | IN H,(C) | 12 | S Z H PV N |
| 0xED68 | IN L,(C) | 12 | S Z H PV N |
| 0xED70 | IN (C) | 12 | S Z H PV N |
| 0xED78 | IN A,(C) | 12 | S Z H PV N |
| 0xDB | IN A,(n) | 11 | - |

#### Output Instructions
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0xED41 | OUT (C),B | 12 | - |
| 0xED49 | OUT (C),C | 12 | - |
| 0xED51 | OUT (C),D | 12 | - |
| 0xED59 | OUT (C),E | 12 | - |
| 0xED61 | OUT (C),H | 12 | - |
| 0xED69 | OUT (C),L | 12 | - |
| 0xED71 | OUT (C),0 | 12 | - |
| 0xED79 | OUT (C),A | 12 | - |
| 0xD3 | OUT (n),A | 11 | - |

#### Block Transfer Instructions
| Opcode | Mnemonic | T-States | Flags | Operation |
|--------|----------|----------|-------|-----------|
| 0xEDA0 | LDI | 16 | H PV N | (DE) = (HL), HL++, DE++, BC-- |
| 0xEDA8 | LDD | 16 | H PV N | (DE) = (HL), HL--, DE--, BC-- |
| 0xEDB0 | LDIR | 21/16 | H PV N | Repeat LDI until BC=0 |
| 0xEDB8 | LDDR | 21/16 | H PV N | Repeat LDD until BC=0 |

#### Block Compare Instructions
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0xEDA1 | CPI | 16 | S Z H PV N |
| 0xEDA9 | CPD | 16 | S Z H PV N |
| 0xEDB1 | CPIR | 21/16 | S Z H PV N |
| 0xEDB9 | CPDR | 21/16 | S Z H PV N |

#### Block I/O Instructions
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0xEDA2 | INI | 16 | S Z H PV N |
| 0xEDAA | IND | 16 | S Z H PV N |
| 0xEDB2 | INIR | 21/16 | S Z H PV N |
| 0xEDBA | INDR | 21/16 | S Z H PV N |
| 0xEDA3 | OUTI | 16 | S Z H PV N |
| 0xEDAB | OUTD | 16 | S Z H PV N |
| 0xEDB3 | OTIR | 21/16 | S Z H PV N |
| 0xEDBB | OTDR | 21/16 | S Z H PV N |

#### Interrupt and Special Instructions
| Opcode | Mnemonic | T-States | Flags | Operation |
|--------|----------|----------|-------|-----------|
| 0xED44 | NEG | 8 | S Z H PV N C | A = 0 - A |
| 0xED46 | IM 0 | 8 | - | Set interrupt mode 0 |
| 0xED56 | IM 1 | 8 | - | Set interrupt mode 1 |
| 0xED5E | IM 2 | 8 | - | Set interrupt mode 2 |
| 0xED4D | RETI | 14 | - | Return from interrupt |
| 0xED45 | RETN | 14 | - | Return from NMI |
| 0xED47 | LD I,A | 9 | - | Load A to I register |
| 0xED57 | LD A,I | 9 | S Z H PV N | Load I to A |
| 0xED4F | LD R,A | 9 | - | Load A to R register |
| 0xED5F | LD A,R | 9 | S Z H PV N | Load R to A |
| 0xED6F | RLD | 18 | S Z H PV N | Rotate left decimal |
| 0xED67 | RRD | 18 | S Z H PV N | Rotate right decimal |

### 9. Miscellaneous Instructions

#### Special Operations
| Opcode | Mnemonic | T-States | Flags | Operation |
|--------|----------|----------|-------|-----------|
| 0x00 | NOP | 4 | - | No operation |
| 0x76 | HALT | 4 | - | Stop execution |
| 0xF3 | DI | 4 | - | Disable interrupts |
| 0xFB | EI | 4 | - | Enable interrupts |

#### Rotate and Decimal Instructions
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0x07 | RLCA | 4 | N C |
| 0x0F | RRCA | 4 | N C |
| 0x17 | RLA | 4 | N C |
| 0x1F | RRA | 4 | N C |
| 0x27 | DAA | 4 | S Z H PV N C |
| 0x2F | CPL | 4 | H N |
| 0x37 | SCF | 4 | H N C |
| 0x3F | CCF | 4 | H N C |

#### Exchange Instructions
| Opcode | Mnemonic | T-States | Flags |
|--------|----------|----------|-------|
| 0x08 | EX AF,AF' | 4 | - |
| 0xD9 | EXX | 4 | - |
| 0xEB | EX DE,HL | 4 | - |
| 0xE3 | EX (SP),HL | 19 | - |

### 10. IX Register Instructions (DD prefix)

All HL operations can be prefixed with 0xDD to use IX instead of HL.
Displacement byte (d) follows immediately after DD CB for indexed bit operations.

#### Examples:
- 0xDD21 = LD IX,nn
- 0xDD7E = LD A,(IX+d)
- 0xDD77 = LD (IX+d),A
- 0xDDCB = IX bit operations (4-byte instructions: DD CB d xx)

### 11. IY Register Instructions (FD prefix)

Similar to IX operations but with IY register (0xFD prefix).

### 12. Undocumented Instructions

#### Undocumented Single-Byte Operations
| Opcode | Mnemonic | T-States | Flags | Operation |
|--------|----------|----------|-------|-----------|
| 0xCB30-37 | SLL r | 8 | S Z PV C | Shift left logical (sets bit 0) |

#### Undocumented IX/IY Operations
- IXH, IXL, IYH, IYL can be accessed as individual 8-bit registers
- All r operations work with IX/IY high/low bytes
- Timing may differ from documented instructions

#### Undocumented Flag Behavior
- Some instructions set undocumented flags (bits 3 and 5 of F register)
- Flag behavior may vary between Z80 models
- Block instructions have complex undocumented flag effects

### Implementation Notes

1. **Prefix Handling**: DD/FD prefixes can be repeated and stacked
2. **Timing Variations**: Some instructions have different timing on different Z80 models
3. **Interrupt Timing**: EI enables interrupts after the next instruction
4. **R Register**: Increments on every instruction fetch (M1 cycle)
5. **Undocumented Behavior**: Many edge cases exist, especially with flag handling

## T-State Timing Rules

- **Basic instructions**: 4-20 T-states
- **Memory access**: +3 T-states per byte
- **Conditional branches**: Additional T-states when condition is met
- **Block instructions**: Variable timing based on register values
- **Prefixed instructions**: Base timing + prefix overhead

This reference provides the foundation for implementing a complete Z80 CPU emulator with full instruction set compatibility.