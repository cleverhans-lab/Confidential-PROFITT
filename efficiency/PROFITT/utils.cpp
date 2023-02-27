

// convert (emp) 32-bit Integer to Float
Float Int32ToFloat(Integer r) {
        Float output(0.0, PUBLIC);
        const Integer twenty_three(8, 23, PUBLIC);

        Bit signBit = r.bits[31];
        Integer unsignedInput = r.abs();

        Integer firstOneIdx = Integer(8, 31, PUBLIC) - unsignedInput.leading_zeros().resize(8);
        Bit leftShift = firstOneIdx >= twenty_three;

        Integer shiftOffset = If(leftShift, firstOneIdx - twenty_three, twenty_three - firstOneIdx);
        Integer shifted = If(leftShift, unsignedInput >> shiftOffset, unsignedInput << shiftOffset);

        Integer exponent = firstOneIdx + Integer(8, 127, PUBLIC);

        output.value[31] = signBit;
        memcpy(output.value.data()+23, exponent.bits.data(), 8*sizeof(block));
        memcpy(output.value.data(), shifted.bits.data(), 23*sizeof(block));

        return output;
}


Integer slice_integer(int start, int end, const Integer & input) {
	int len = end - start;	
	Integer x(len, 0, PUBLIC); // initialize output integer
	memcpy(x.bits.data(), input.bits.data()+start, len*sizeof(Bit)); // replace x's bit string with bits from input

	return x;
}